# Technical Documentation

## Annotation Processing Lifecycle

```
┌────────────────────────────────────────────────────────────┐
│ 1. COMPILATION STARTS                                      │
│    javac UserResource.java                                 │
└────────────────────────────────────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────────┐
│ 2. JAVAC DISCOVERS ANNOTATION PROCESSORS                   │
│    - Reads META-INF/services/javax.annotation.processing   │
│    - Loads JaxRsProcessor                                  │
└────────────────────────────────────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────────┐
│ 3. PROCESSOR ROUND 1: PROCESS ANNOTATIONS                  │
│    JaxRsProcessor.process() called                         │
│    - Finds @Path classes                                   │
│    - Finds @Provider filter classes                        │
│    - Analyzes methods, parameters, return types            │
└────────────────────────────────────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────────┐
│ 4. CODE GENERATION                                         │
│    JaxRsProcessor.processResourceClass()                   │
│    - Uses Helidon ClassModel for code generation           │
│    - Generates handler methods with filter support         │
│    - Writes to Filer                                       │
│    → UserResource$$JaxRsRouting.java                       │
└────────────────────────────────────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────────┐
│ 5. COMPILE GENERATED CODE                                  │
│    javac UserResource$$JaxRsRouting.java                   │
└────────────────────────────────────────────────────────────┘
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

// Add constructor using consumer pattern
classBuilder.addConstructor(ctor -> ctor
        .accessModifier(AccessModifier.PUBLIC)
        .addContent("this.resource = new ")
        .addContent(resourceTypeName)
        .addContentLine("();"));
```

### TypeName for Automatic Imports

Using `TypeName` with `addContent()` automatically manages imports:

```java
private static final TypeName SERVER_REQUEST =
    TypeName.create("io.helidon.webserver.http.ServerRequest");

handler.addContent(SERVER_REQUEST)  // Adds import automatically
       .addContent(" req");
```

### Generic Types

Building generic types like `List<ContainerRequestFilter>`:

```java
TypeName listOfFilters = TypeName.builder(LIST)
        .addTypeArgument(requestFilterType)
        .build();
```

## Handler Generation

For each JAX-RS method, the processor generates a handler:

```java
private Method generateHandler(RouteInfo route, String resourceClassName) {
    Method.Builder handler = Method.builder()
            .name(route.handlerName())
            .accessModifier(AccessModifier.PRIVATE)
            .addParameter(p -> p.name("req").type(SERVER_REQUEST))
            .addParameter(p -> p.name("res").type(SERVER_RESPONSE));

    handler.addContentLine("try {");
    handler.increaseContentPadding();

    // Generate request filter execution
    handler.addContent(requestContext)
           .addContent(" requestContext = new ")
           .addContent(requestContext)
           .addContentLine("(req);");
    handler.addContentLine("for (var filter : requestFilters) {");
    handler.increaseContentPadding();
    handler.addContentLine("filter.filter(requestContext);");
    handler.addContentLine("if (requestContext.isAborted()) {");
    // ... abort handling
    handler.decreaseContentPadding();
    handler.addContentLine("}");

    // Generate parameter extraction
    for (VariableElement param : method.getParameters()) {
        extractParameter(param, handler);
    }

    // Generate method invocation
    handler.addContent("resource.")
           .addContent(methodName)
           .addContent("(")
           .addContent(args)
           .addContentLine(");");

    // ... response handling and exception catching

    return handler.build();
}
```

## Parameter Extraction

The processor analyzes parameter annotations and generates extraction code:

```java
private String extractParameter(VariableElement param, Method.Builder handler) {
    // @PathParam
    PathParam pathParam = param.getAnnotation(PathParam.class);
    if (pathParam != null) {
        handler.addContent(typeName)
               .addContent(" ")
               .addContent(varName)
               .addContent(" = ");
        handler.addContent(convertType(
            "req.path().pathParameters().get(\"" + pathParam.value() + "\")",
            type));
        handler.addContentLine(";");
        return varName;
    }

    // @QueryParam with @DefaultValue support
    QueryParam queryParam = param.getAnnotation(QueryParam.class);
    DefaultValue defaultValue = param.getAnnotation(DefaultValue.class);
    if (queryParam != null) {
        String orElse = defaultValue != null
            ? "\"" + defaultValue.value() + "\""
            : "null";
        handler.addContent(convertType(
            "req.query().first(\"" + queryParam.value() + "\").orElse(" + orElse + ")",
            type));
        // ...
    }

    // @CookieParam
    // @FormParam
    // @HeaderParam
    // @Context (UriInfo, HttpHeaders)
    // Body parameter (JSON deserialization)
}
```

## Filter Integration

Filters annotated with `@Provider` are detected and integrated:

```java
@Override
public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    // Collect filter classes
    for (Element element : roundEnv.getElementsAnnotatedWith(Provider.class)) {
        if (element.getKind() == ElementKind.CLASS) {
            TypeElement typeElement = (TypeElement) element;
            if (implementsInterface(typeElement, "jakarta.ws.rs.container.ContainerRequestFilter")) {
                requestFilters.add(typeElement);
            }
            if (implementsInterface(typeElement, "jakarta.ws.rs.container.ContainerResponseFilter")) {
                responseFilters.add(typeElement);
            }
        }
    }
    // ...
}
```

Generated constructor initializes filters:

```java
public UserResource$$JaxRsRouting() {
    this.resource = new UserResource();
    this.objectMapper = new ObjectMapper();
    this.requestFilters = new ArrayList<>();
    this.requestFilters.add(new LoggingFilter());
    this.responseFilters = new ArrayList<>();
    this.responseFilters.add(new LoggingFilter());
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

## Runtime Wrappers

The runtime package provides JAX-RS interface implementations wrapping Helidon types:

### HelidonUriInfo

Wraps `ServerRequest` to implement `jakarta.ws.rs.core.UriInfo`:

```java
public class HelidonUriInfo implements UriInfo {
    private final ServerRequest request;

    @Override
    public String getPath() {
        return request.path().path();
    }

    @Override
    public MultivaluedMap<String, String> getQueryParameters() {
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        request.query().toMap().forEach((key, values) -> {
            for (String value : values) {
                params.add(key, value);
            }
        });
        return params;
    }
    // ...
}
```

### HelidonContainerRequestContext

Implements `ContainerRequestContext` for request filters:

```java
public class HelidonContainerRequestContext implements ContainerRequestContext {
    private final ServerRequest request;
    private boolean aborted = false;
    private int abortStatus;

    @Override
    public void abortWith(Response response) {
        this.aborted = true;
        this.abortStatus = response.getStatus();
    }

    public boolean isAborted() {
        return aborted;
    }
    // ...
}
```

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

    <!-- Phase 2: Run annotation processor -->
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

2. Recompile - the processor automatically generates updated code.

### Adding Custom Annotations

1. Define annotation:

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface RateLimit {
    int value();
}
```

2. Detect and generate code in processor:

```java
RateLimit rateLimit = method.getAnnotation(RateLimit.class);
if (rateLimit != null) {
    handler.addContentLine("if (!rateLimiter.tryAcquire()) {");
    handler.increaseContentPadding();
    handler.addContentLine("res.status(429).send(\"Rate limit exceeded\");");
    handler.addContentLine("return;");
    handler.decreaseContentPadding();
    handler.addContentLine("}");
}
```
