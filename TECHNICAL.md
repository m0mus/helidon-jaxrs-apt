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

    // 3. Parameter extraction
    List<String> paramNames = extractParameters(route.method(), handler);

    // 4. Method invocation with reader interceptors
    generateMethodInvocation(handler, route, paramNames);

    // 5. Response filters
    generateResponseFilters(handler);

    // 6. Writer interceptors and response sending
    generateResponseSending(handler);

    // 7. Exception handling with ExceptionMapper support
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
```

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
