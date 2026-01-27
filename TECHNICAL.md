# Technical Documentation

## Annotation Processing Lifecycle

```
+------------------------------------------------------------+
| 1. COMPILATION STARTS                                      |
|    javac UserResource.java                                 |
+------------------------------------------------------------+
                         |
                         v
+------------------------------------------------------------+
| 2. JAVAC DISCOVERS ANNOTATION PROCESSORS                   |
|    - Reads META-INF/services/javax.annotation.processing   |
|    - Loads JaxRsProcessor                                  |
+------------------------------------------------------------+
                         |
                         v
+------------------------------------------------------------+
| 3. PROCESSOR ROUND 1: COLLECT PROVIDERS                    |
|    JaxRsProcessor.process() called                         |
|    - Finds @Provider filter classes                        |
|    - Finds @Provider interceptor classes                   |
|    - Finds @Provider ExceptionMapper classes               |
|    - Detects @Priority, @PreMatching, @NameBinding         |
+------------------------------------------------------------+
                         |
                         v
+------------------------------------------------------------+
| 4. PROCESSOR ROUND 2: PROCESS RESOURCES                    |
|    - Finds @Path resource classes                          |
|    - Analyzes methods, parameters, return types            |
|    - Detects @NameBinding on methods/classes               |
+------------------------------------------------------------+
                         |
                         v
+------------------------------------------------------------+
| 5. CODE GENERATION                                         |
|    JaxRsProcessor.processResourceClass()                   |
|    - Uses Helidon ClassModel for code generation           |
|    - Generates handler methods with filter support         |
|    - Writes to Filer                                       |
|    -> UserResource$$JaxRsRouting.java                      |
+------------------------------------------------------------+
                         |
                         v
+------------------------------------------------------------+
| 6. COMPILE GENERATED CODE                                  |
|    javac UserResource$$JaxRsRouting.java                   |
+------------------------------------------------------------+
```

## Code Generation with Helidon ClassModel

The processor uses Helidon's `io.helidon.codegen.classmodel` for type-safe code generation:

```java
ClassModel.Builder classBuilder = ClassModel.builder()
        .packageName(packageName)
        .name(generatedClassName)
        .accessModifier(AccessModifier.PUBLIC)
        .isFinal(true)
        .addAnnotation(ann -> ann
            .type(GENERATED)
            .addParameter("value", JaxRsProcessor.class.getName()));

// Add fields
classBuilder.addField(Field.builder()
        .name("resource")
        .type(resourceTypeName)
        .accessModifier(AccessModifier.PRIVATE)
        .isFinal(true)
        .build());

classBuilder.addField(Field.builder()
        .name("filterContext")
        .type(TypeName.create("io.helidon.examples.jaxrs.apt.runtime.FilterContext"))
        .accessModifier(AccessModifier.PRIVATE)
        .isFinal(true)
        .build());
```

### TypeName for Automatic Imports

Using `TypeName` with `addContent()` automatically manages imports:

```java
private static final TypeName SERVER_REQUEST =
    TypeName.create("io.helidon.webserver.http.ServerRequest");

handler.addContent(SERVER_REQUEST)  // Adds import automatically
       .addContent(" req");
```

## Provider Collection

The processor collects all `@Provider` annotated classes:

### Filters

```java
// Request filters (sorted by @Priority, lower = first)
private List<FilterInfo> requestFilters = new ArrayList<>();

// Response filters (sorted by @Priority reversed, lower = last)
private List<FilterInfo> responseFilters = new ArrayList<>();

// Pre-matching filters (execute before route matching)
private List<FilterInfo> preMatchingRequestFilters = new ArrayList<>();
```

### Interceptors

```java
// Reader interceptors (for request body processing)
private List<FilterInfo> readerInterceptors = new ArrayList<>();

// Writer interceptors (for response body processing)
private List<FilterInfo> writerInterceptors = new ArrayList<>();
```

### Exception Mappers

```java
// Maps exception types to their mappers
private List<ExceptionMapperInfo> exceptionMappers = new ArrayList<>();

private record ExceptionMapperInfo(TypeElement typeElement, String exceptionType, int priority) {}
```

Detection of exception type from generic parameter:

```java
private String getExceptionMapperType(TypeElement typeElement) {
    for (TypeMirror iface : typeElement.getInterfaces()) {
        String ifaceStr = iface.toString();
        if (ifaceStr.startsWith("jakarta.ws.rs.ext.ExceptionMapper<")) {
            int start = ifaceStr.indexOf('<') + 1;
            int end = ifaceStr.lastIndexOf('>');
            return ifaceStr.substring(start, end);
        }
    }
    return null;
}
```

## Handler Generation

For each JAX-RS method, the processor generates a handler:

```java
private void generateHandler(RouteInfo route, ClassModel.Builder classBuilder) {
    Method.Builder handler = Method.builder()
            .name(route.handlerName())
            .accessModifier(AccessModifier.PRIVATE)
            .addParameter(p -> p.name("req").type(SERVER_REQUEST))
            .addParameter(p -> p.name("res").type(SERVER_RESPONSE));

    // 1. Pre-matching filters
    generatePreMatchingFilters(handler);

    // 2. Request filters (with name binding support)
    generateRequestFilters(handler, route.nameBindings());

    // 3. Content negotiation
    generateContentNegotiation(handler, route);

    // 4. Parameter extraction
    List<String> paramNames = extractParameters(route.method(), handler);

    // 5. Method invocation with reader interceptors
    generateMethodInvocation(handler, route, paramNames);

    // 6. Response filters
    generateResponseFilters(handler);

    // 7. Writer interceptors and response sending
    generateResponseSending(handler);

    // 8. Exception handling with ExceptionMapper support
    generateExceptionHandling(handler);
}
```

## Parameter Extraction

The processor analyzes parameter annotations and generates extraction code:

### Basic Parameters

```java
// @PathParam
handler.addContent("String _id = req.path().pathParameters().get(\"id\");");
handler.addContent("Long id = _id != null ? Long.parseLong(_id) : null;");

// @QueryParam with @DefaultValue
handler.addContent("String _name = req.query().first(\"name\").orElse(\"default\");");

// @HeaderParam
handler.addContent("String auth = req.headers().first(HeaderNames.create(\"Authorization\")).orElse(null);");

// @CookieParam
handler.addContent("String session = req.headers().cookies().first(\"session\").orElse(null);");

// @FormParam (requires caching form data)
handler.addContent("String field = _formParams.first(\"field\").orElse(null);");

// @MatrixParam
handler.addContent("String _color = req.path().matrixParameters().first(\"color\").orElse(null);");
handler.addContent("String color = _color;");
```

### Collection Parameters (List/Set)

```java
// List<String> @QueryParam
handler.addContent("List<String> _tags_raw = req.query().all(\"tag\", List::of);");
handler.addContent("List<String> tags = new ArrayList<>(_tags_raw);");

// List<Long> with conversion
handler.addContent("List<String> _ids_raw = req.query().all(\"id\", List::of);");
handler.addContent("List<Long> ids = _ids_raw.stream().map(Long::parseLong).collect(Collectors.toList());");

// Set<String> (removes duplicates)
handler.addContent("List<String> _tags_raw = req.query().all(\"tag\", List::of);");
handler.addContent("Set<String> tags = new HashSet<>(_tags_raw);");
```

### @BeanParam

```java
// Create bean instance
handler.addContent("SearchParams params = new SearchParams();");

// Extract fields from bean class annotations
handler.addContent("String _q = req.query().first(\"q\").orElse(null);");
handler.addContent("params.setQuery(_q);");

handler.addContent("String _page = req.query().first(\"page\").orElse(\"1\");");
handler.addContent("if (_page != null) { params.setPage(Integer.parseInt(_page)); }");
```

### @Context

```java
// UriInfo
handler.addContent("UriInfo uriInfo = new HelidonUriInfo(req);");

// HttpHeaders
handler.addContent("HttpHeaders headers = new HelidonHttpHeaders(req);");

// SecurityContext
handler.addContent("SecurityContext securityContext = new HelidonSecurityContext(req);");
```

### SecurityContext Implementation

`HelidonSecurityContext` extracts security information from HTTP headers:

- **getUserPrincipal()**: Extracts username from Basic auth header (base64 decoded)
- **isUserInRole(role)**: Checks against `X-User-Roles` header (comma-separated)
- **isSecure()**: Returns true if scheme is HTTPS
- **getAuthenticationScheme()**: Returns BASIC, BEARER, or DIGEST based on Authorization header

## Content Negotiation

The processor validates Content-Type and Accept headers against @Consumes and @Produces annotations:

### @Consumes Validation

For POST/PUT/PATCH methods with @Consumes, validates Content-Type header:

```java
// Content-Type validation
String _contentType = req.headers().contentType().map(ct -> ct.mediaType().text()).orElse(null);
if (_contentType != null && !matchesMediaType(_contentType, new String[]{"application/json"})) {
    res.status(415).send("Unsupported Media Type");
    return;
}
```

### Accept Header Validation

For methods with @Produces, validates Accept header:

```java
// Accept header validation
String _accept = req.headers().first(HeaderNames.ACCEPT).orElse("*/*");
if (!acceptsMediaType(_accept, new String[]{"application/json", "application/xml"})) {
    res.status(406).send("Not Acceptable");
    return;
}
```

### Media Type Matching

Generated helper methods support:
- Exact matches (`application/json`)
- Wildcard types (`*/*`, `text/*`, `application/*`)
- Quality factors in Accept header (`text/html, application/json;q=0.9`)
- Content-Type parameters (`application/json; charset=utf-8`)

## Response Handling

### POJO Return Types

```java
Object result = resource.getUsers();
HelidonContainerResponseContext responseContext = new HelidonContainerResponseContext(200, result);
// Run response filters...
String json = objectMapper.writeValueAsString(responseContext.getEntity());
res.status(200).header("Content-Type", "application/json").send(json);
```

### jakarta.ws.rs.core.Response Return Type

```java
Response jaxrsResponse = resource.createUser(user);
int status = jaxrsResponse.getStatus();
Object entity = jaxrsResponse.getEntity();

// Copy headers from Response
for (var hdr : jaxrsResponse.getStringHeaders().entrySet()) {
    for (var val : hdr.getValue()) {
        res.header(HeaderNames.create(hdr.getKey()), val);
    }
}

// Send based on content type
if (entity instanceof String) {
    res.status(status).send((String) entity);
} else if (entity != null) {
    res.status(status).send(objectMapper.writeValueAsString(entity));
} else {
    res.status(status).send();
}
```

## Exception Handling

The processor generates a comprehensive exception handling chain:

```java
} catch (Throwable _ex) {
    // 1. Check for custom ExceptionMapper
    ExceptionMapper<Throwable> mapper = filterContext.findExceptionMapper(_ex);
    if (mapper != null) {
        Response response = mapper.toResponse(_ex);
        // Send mapped response...
        return;
    }

    // 2. Handle standard JAX-RS exceptions
    if (_ex instanceof NotFoundException) {
        res.status(404).send(_ex.getMessage());
    } else if (_ex instanceof BadRequestException) {
        res.status(400).send(_ex.getMessage());
    } else if (_ex instanceof NotAuthorizedException) {
        res.status(401).send(_ex.getMessage());
    }
    // ... other JAX-RS exceptions

    // 3. Default 500 for unknown exceptions
    } else {
        res.status(500).send("Internal Server Error: " + _ex.getMessage());
    }
}
```

## Filter Architecture

This section explains how JAX-RS filters are implemented, addressing key design challenges around filter timing, field injection, and registration.

### Design Challenges and Solutions

#### Challenge 1: Filters Need Access to Resource Method (ResourceInfo)

**Problem**: JAX-RS filters may need to access the matched resource method to analyze its annotations (e.g., for authorization checks). However, Helidon filters execute before routing, so the matched method is not yet known.

**Solution**: Two types of filters with different capabilities:

| Filter Type | When it Runs | Has ResourceInfo? | Use Case |
|------------|--------------|-------------------|----------|
| **Pre-matching** (`@PreMatching`) | Before routing | ❌ No | URI rewriting, method override, early abort |
| **Post-matching** (default) | After routing, before handler | ✅ Yes | Authorization, logging, auditing |

Post-matching filters have access to `@Context ResourceInfo` which provides:
- `getResourceClass()` - The matched resource class
- `getResourceMethod()` - The matched method (with annotations)

```java
@Provider
@Priority(100)
public class AuthorizationFilter implements ContainerRequestFilter {
    @Context
    private ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        Method method = resourceInfo.getResourceMethod();
        if (method.isAnnotationPresent(RolesAllowed.class)) {
            // Check authorization based on method annotations
        }
    }
}
```

#### Challenge 2: Request-Scoped Field Injection in Singleton Filters

**Problem**: Filters are instantiated once at startup (singletons), but `@Context` fields like `UriInfo`, `HttpHeaders`, and `SecurityContext` are request-scoped. Traditional approaches using `ThreadLocal` don't work well with virtual threads.

**Solution**: Proxy pattern using Helidon's Context mechanism (virtual-thread safe):

1. **At filter construction time**: Inject proxy objects (e.g., `UriInfoProxy`) into `@Context` fields
2. **At request time**: Register actual request-scoped objects in Helidon's Context
3. **When proxy methods are called**: Delegate to the actual object via `Contexts.context()`

```
Request Flow:
┌─────────────────────────────────────────────────────────────────────┐
│ 1. JaxRsContextFilter                                               │
│    - Registers UriInfo, HttpHeaders, SecurityContext in Context     │
│    - Wraps chain with Contexts.runInContext(req.context(), ...)     │
├─────────────────────────────────────────────────────────────────────┤
│ 2. Pre-matching Filters (if any)                                    │
│    - Can access: UriInfo, HttpHeaders, SecurityContext              │
│    - Cannot access: ResourceInfo (not yet routed)                   │
├─────────────────────────────────────────────────────────────────────┤
│ 3. Helidon Routing (route matching)                                 │
├─────────────────────────────────────────────────────────────────────┤
│ 4. Handler registers ResourceInfo in Context                        │
├─────────────────────────────────────────────────────────────────────┤
│ 5. Post-matching Request Filters                                    │
│    - Can access: All @Context types including ResourceInfo          │
├─────────────────────────────────────────────────────────────────────┤
│ 6. Handler executes resource method                                 │
├─────────────────────────────────────────────────────────────────────┤
│ 7. Response Filters                                                 │
│    - Can access: All @Context types                                 │
└─────────────────────────────────────────────────────────────────────┘
```

**Proxy Implementation Example**:

```java
public class UriInfoProxy implements UriInfo {
    public static final UriInfoProxy INSTANCE = new UriInfoProxy();

    private UriInfo delegate() {
        return Contexts.context()
                .flatMap(ctx -> ctx.get(UriInfo.class, UriInfo.class))
                .orElseThrow(() -> new IllegalStateException(
                        "UriInfo not available - ensure JaxRsContextFilter is registered"));
    }

    @Override
    public String getPath() {
        return delegate().getPath();  // Delegates to request-scoped UriInfo
    }
    // ... other methods delegate similarly
}
```

**Supported @Context Types in Filters**:

| Context Type | Pre-matching Filter | Post-matching Filter |
|--------------|---------------------|---------------------|
| `UriInfo` | ✅ | ✅ |
| `HttpHeaders` | ✅ | ✅ |
| `SecurityContext` | ✅ | ✅ |
| `ResourceInfo` | ❌ (throws) | ✅ |

#### Challenge 3: Filter Registration

**Problem**: How are filters discovered and registered?

**Solution**: APT-based discovery with `@Provider` annotation:

1. **Discovery**: At compile time, `JaxRsProcessor` scans for `@Provider` classes
2. **Classification**: Filters are classified by type and sorted by `@Priority`
3. **Code Generation**: Registration code is generated in the routing class

```java
// Generated code in register() method:

// 1. Context propagation filter (enables @Context injection)
routing.addFilter(JaxRsContextFilter.INSTANCE);

// 2. Pre-matching filters (as Helidon Filter, runs before routing)
var _preMatchFilter0 = new UriRewritingFilter();
FilterContext.injectContextProxies(_preMatchFilter0);
_preMatchingFilters.add(_preMatchFilter0);
routing.addFilter(new JaxRsPreMatchingFilter(_preMatchingFilters, this.filterContext));

// 3. Post-matching filters (registered in FilterContext, called from handlers)
var _addRequestFilter0 = new AuthFilter();
FilterContext.injectContextProxies(_addRequestFilter0);
this.filterContext.addRequestFilter(_addRequestFilter0);
```

### Pre-matching vs Post-matching Filters

#### Pre-matching Filters (`@PreMatching`)

Execute BEFORE Helidon's routing, can modify which route is matched:

```java
@Provider
@PreMatching
@Priority(100)
public class UriRewritingFilter implements ContainerRequestFilter {
    @Context
    private UriInfo uriInfo;  // ✅ Available

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        // Rewrite legacy URLs to new paths
        if (ctx.getUriInfo().getPath().startsWith("/legacy/")) {
            ctx.setRequestUri(URI.create("/api/v2/..."));
        }
    }
}
```

**Capabilities**:
- Modify request URI (affects routing)
- Override HTTP method (e.g., POST → PUT via header)
- Abort request before routing
- Access `@Context UriInfo`, `HttpHeaders`, `SecurityContext`

**Limitations**:
- Cannot access `@Context ResourceInfo` (route not yet matched)
- Cannot use `@NameBinding` (no method to bind to yet)

#### Post-matching Filters (default)

Execute AFTER routing, have full context:

```java
@Provider
@Priority(100)
public class AuditFilter implements ContainerRequestFilter {
    @Context
    private ResourceInfo resourceInfo;  // ✅ Available

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        Method method = resourceInfo.getResourceMethod();
        Class<?> resource = resourceInfo.getResourceClass();
        // Log: "Calling UserResource.getUser()"
    }
}
```

**Capabilities**:
- Access all `@Context` types including `ResourceInfo`
- Use `@NameBinding` for selective application
- Full request/response modification

### Name Binding

Selectively apply filters to specific methods:

```java
// 1. Define binding annotation
@NameBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Authenticated {}

// 2. Apply to filter
@Provider
@Authenticated
public class AuthFilter implements ContainerRequestFilter { ... }

// 3. Apply to methods that need authentication
@Path("/api")
public class ApiResource {
    @GET
    @Path("/public")
    public String publicData() { ... }  // No AuthFilter

    @GET
    @Path("/private")
    @Authenticated
    public String privateData() { ... }  // AuthFilter applied
}
```

### Filter Priority

Lower priority value = executes earlier for request filters, later for response filters:

```
Request flow:  Priority 100 → Priority 200 → Priority 300 → Handler
Response flow: Handler → Priority 300 → Priority 200 → Priority 100
```

```java
@Provider
@Priority(100)  // Runs first for requests
public class EarlyFilter implements ContainerRequestFilter { ... }

@Provider
@Priority(200)  // Runs second for requests
public class LaterFilter implements ContainerRequestFilter { ... }
```

### Implementation Classes

| Class | Purpose |
|-------|---------|
| `JaxRsContextFilter` | Helidon Filter that sets up context propagation and registers request-scoped objects |
| `JaxRsPreMatchingFilter` | Helidon Filter wrapper for JAX-RS pre-matching filters |
| `FilterContext` | Registry for filters, interceptors, exception mappers; handles @Context injection |
| `UriInfoProxy` | Proxy for request-scoped UriInfo delegation |
| `HttpHeadersProxy` | Proxy for request-scoped HttpHeaders delegation |
| `SecurityContextProxy` | Proxy for request-scoped SecurityContext delegation |
| `ResourceInfoProxy` | Proxy for request-scoped ResourceInfo delegation |
| `HelidonResourceInfo` | Adapter implementing ResourceInfo for matched resource |

### Limitations

1. **CDI not supported**: Filters are instantiated with `new`, not through dependency injection. Only `@Context` fields are injected (via proxies).

2. **ResourceInfo in pre-matching**: Pre-matching filters cannot access `ResourceInfo` because route matching hasn't occurred yet. This is by design and matches JAX-RS specification.

3. **Custom @Context types**: Only the standard JAX-RS context types are supported (`UriInfo`, `HttpHeaders`, `SecurityContext`, `ResourceInfo`).

## Filter Context

The `FilterContext` class manages all providers:

```java
public class FilterContext {
    private final List<ContainerRequestFilter> preMatchingRequestFilters;
    private final List<FilterEntry<ContainerRequestFilter>> requestFilters;
    private final List<FilterEntry<ContainerResponseFilter>> responseFilters;
    private final List<InterceptorEntry<ReaderInterceptor>> readerInterceptors;
    private final List<InterceptorEntry<WriterInterceptor>> writerInterceptors;
    private final List<ExceptionMapperEntry<?>> exceptionMappers;

    // Name binding support
    public record FilterEntry<T>(T filter, Set<String> nameBindings) {
        public boolean matches(Set<String> methodBindings) {
            if (nameBindings.isEmpty()) return true;  // Global filter
            return nameBindings.stream().anyMatch(methodBindings::contains);
        }
    }

    // Exception mapper lookup with inheritance
    public <T extends Throwable> ExceptionMapper<T> findExceptionMapper(T exception) {
        // First try exact match, then walk up inheritance chain
        // Returns most specific mapper
    }
}
```

## SimpleRuntimeDelegate

Required for `Response.status()`, `Response.ok()` etc. to work:

```java
public class SimpleRuntimeDelegate extends RuntimeDelegate {
    static {
        RuntimeDelegate.setInstance(new SimpleRuntimeDelegate());
    }

    @Override
    public Response.ResponseBuilder createResponseBuilder() {
        return new SimpleResponseBuilder();
    }

    // Header delegates for MediaType, Date, CacheControl
    @Override
    public <T> HeaderDelegate<T> createHeaderDelegate(Class<T> type) {
        if (type == MediaType.class) return new MediaTypeHeaderDelegate();
        // ...
    }
}
```

## Route Ordering

Routes are sorted to ensure specific paths match before parameterized paths:

```java
routes.sort((a, b) -> {
    boolean aParam = a.path.contains("{");
    boolean bParam = b.path.contains("{");
    if (aParam != bParam) return aParam ? 1 : -1;
    int lenDiff = b.path.length() - a.path.length();
    return lenDiff != 0 ? lenDiff : a.path.compareTo(b.path);
});
```

This ensures `/users/count` is registered before `/users/{id}`.

## Maven Multi-Phase Compilation

Since the processor and resources are in the same project, multi-phase compilation is required:

```xml
<executions>
    <!-- Phase 1: Compile processor and runtime classes -->
    <execution>
        <id>compile-processor</id>
        <phase>generate-sources</phase>
        <configuration>
            <proc>none</proc>
            <includes>
                <include>**/processor/**</include>
                <include>**/runtime/**</include>
                <include>**/apt/User.java</include>
                <include>**/apt/UserResource.java</include>
                <include>**/apt/filter/**</include>
            </includes>
        </configuration>
    </execution>

    <!-- Phase 2: Run annotation processor on resources -->
    <execution>
        <id>generate-routing</id>
        <phase>process-sources</phase>
        <configuration>
            <includes>
                <include>**/apt/UserResource.java</include>
                <include>**/apt/filter/**</include>
            </includes>
            <annotationProcessors>
                <annotationProcessor>
                    io.helidon.examples.jaxrs.apt.processor.JaxRsProcessor
                </annotationProcessor>
            </annotationProcessors>
        </configuration>
    </execution>

    <!-- Phase 3: Final compilation -->
    <execution>
        <id>default-compile</id>
        <phase>compile</phase>
        <configuration>
            <proc>none</proc>
        </configuration>
    </execution>

    <!-- Similar phases for test compilation -->
</executions>
```

## Extending the Processor

### Adding New Parameter Types

1. Add annotation detection in `extractParameter()`:

```java
MatrixParam matrixParam = param.getAnnotation(MatrixParam.class);
if (matrixParam != null) {
    handler.addContent(typeName)
           .addContent(" ")
           .addContent(varName)
           .addContent(" = req.path().matrixParameters().first(\"")
           .addContent(matrixParam.value())
           .addContentLine("\").orElse(null);");
    return varName;
}
```

2. Update `isBodyParameter()` to exclude the new annotation.

### Adding Custom Providers

The processor automatically detects classes with `@Provider` that implement:
- `ContainerRequestFilter`
- `ContainerResponseFilter`
- `ReaderInterceptor`
- `WriterInterceptor`
- `ExceptionMapper<T>`

No code changes needed - just add the `@Provider` annotation.

### Adding Custom Annotations

1. Define annotation:

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)  // Note: RUNTIME for @NameBinding
public @interface RateLimit {
    int value();
}
```

2. Detect and generate code in processor:

```java
RateLimit rateLimit = method.getAnnotation(RateLimit.class);
if (rateLimit != null) {
    handler.addContentLine("if (!rateLimiter.tryAcquire(" + rateLimit.value() + ")) {");
    handler.increaseContentPadding();
    handler.addContentLine("res.status(429).send(\"Rate limit exceeded\");");
    handler.addContentLine("return;");
    handler.decreaseContentPadding();
    handler.addContentLine("}");
}
```

## Test Infrastructure

The project uses Helidon's testing framework:

```java
@ServerTest
class ParameterExtractionTest {
    private final WebClient client;

    ParameterExtractionTest(WebClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        new TestResource$$JaxRsRouting().register(routing);
    }

    @Test
    void testPathParam() {
        String response = client.get("/test/123").requestEntity(String.class);
        assertThat(response, is("id:123"));
    }
}
```

Test resources are processed by the same annotation processor during the test compilation phase.

## JAX-RS Specification Coverage

### Implemented Features

| Category | Feature | Status |
|----------|---------|--------|
| **HTTP Methods** | `@GET`, `@POST`, `@PUT`, `@DELETE`, `@PATCH`, `@HEAD`, `@OPTIONS` | ✅ |
| **Paths** | `@Path` on class and methods | ✅ |
| | Path parameters `{param}` | ✅ |
| | Regex path parameters `{param: regex}` | ❌ |
| **Parameters** | `@PathParam` | ✅ |
| | `@QueryParam` | ✅ |
| | `@HeaderParam` | ✅ |
| | `@CookieParam` | ✅ |
| | `@FormParam` | ✅ |
| | `@MatrixParam` | ✅ |
| | `@DefaultValue` | ✅ |
| | `@BeanParam` | ✅ |
| | `List<T>`, `Set<T>` collection params | ✅ |
| | `@Encoded` | ❌ |
| **Content** | `@Produces` | ✅ |
| | `@Consumes` | ✅ |
| | Accept header negotiation | ✅ |
| | Content-Type validation | ✅ |
| | Wildcard media types | ✅ |
| **Return Types** | POJO → JSON | ✅ |
| | `Response` | ✅ |
| | `void` | ✅ |
| | `CompletionStage<T>` | ❌ |
| **Context** | `@Context UriInfo` | ✅ |
| | `@Context HttpHeaders` | ✅ |
| | `@Context SecurityContext` | ✅ |
| | `@Context ResourceInfo` | ✅ |
| | `@Context Request` | ❌ |
| | `@Context Providers` | ❌ |
| | `@Context Application` | ❌ |
| **Filters** | `ContainerRequestFilter` | ✅ |
| | `ContainerResponseFilter` | ✅ |
| | `@PreMatching` | ✅ |
| | `@Priority` | ✅ |
| | `@NameBinding` | ✅ |
| | `@Context` injection in filters | ✅ |
| **Interceptors** | `ReaderInterceptor` | ✅ |
| | `WriterInterceptor` | ✅ |
| **Exceptions** | `ExceptionMapper<T>` | ✅ |
| | Built-in JAX-RS exceptions | ✅ |
| **Resources** | Sub-resource locators | ✅ |
| | Sub-resource classes | ✅ |

### Not Implemented (Future Work)

| Category | Feature | Complexity | Notes |
|----------|---------|------------|-------|
| **DI** | CDI `@Inject` | High | Requires DI container integration |
| | `@Context` field injection in resources | Medium | Currently only method params |
| **Async** | `@Suspended AsyncResponse` | Medium | Virtual threads reduce need |
| | SSE (`SseEventSink`) | Medium | Helidon has native SSE support |
| **Providers** | `MessageBodyReader<T>` | Medium | Custom deserializers |
| | `MessageBodyWriter<T>` | Medium | Custom serializers |
| | `ParamConverter<T>` | Low | Custom param conversion |
| | `ContextResolver<T>` | Low | Context providers |
| **Content** | Multipart (`@FormDataParam`) | Medium | File uploads |
| | JAXB/XML support | Low | Add alongside JSON |
| **Validation** | Bean Validation (`@Valid`) | Medium | Integration with Hibernate Validator |
| **Other** | `@Encoded` | Low | Disable URL decoding |
| | Regex path params | Low | `{id: [0-9]+}` |
| | Hypermedia/HATEOAS | Low | `Link` headers |

### Design Decisions

#### Why No CDI Support?

1. **Simplicity**: This project focuses on compile-time code generation without runtime DI overhead
2. **GraalVM**: Avoiding reflection-based DI improves native image compatibility
3. **Helidon Integration**: Helidon 4.x uses its own injection framework; full CDI would require significant integration work

**Workaround**: Use constructor injection with a service locator or factory pattern.

#### Why Virtual Threads Instead of Async API?

Helidon 4.x runs on virtual threads by default. This means:
- Blocking code is efficient (no thread pool exhaustion)
- Simpler programming model than `AsyncResponse`
- Better stack traces for debugging

`CompletionStage<T>` support could be added for compatibility, but isn't necessary for performance.

#### Why Proxies for @Context in Filters?

1. **Singleton Filters**: Filters are instantiated once, but `@Context` objects are request-scoped
2. **Virtual Threads**: `ThreadLocal` has overhead with virtual threads
3. **Helidon Context**: Using `Contexts.context()` integrates with Helidon's existing infrastructure

### Comparison with Helidon MP (Jersey)

| Aspect | This Project (APT) | Helidon MP (Jersey) |
|--------|-------------------|---------------------|
| Code Generation | Compile-time | Runtime |
| Reflection | None | Heavy |
| Native Image | Excellent | Requires configuration |
| Startup Time | Fast | Slower |
| JAX-RS Coverage | Partial (see above) | Full |
| CDI Support | No | Yes |
| Footprint | Smaller | Larger |

**Use This Project When**:
- Building microservices with specific JAX-RS subset
- Targeting GraalVM native image
- Prioritizing startup time and footprint
- Don't need CDI or advanced JAX-RS features

**Use Helidon MP When**:
- Need full JAX-RS specification compliance
- Require CDI dependency injection
- Using MicroProfile specifications
- Migrating existing JAX-RS applications
