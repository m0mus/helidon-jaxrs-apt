# Helidon JAX-RS APT

Build-time JAX-RS annotation processing for Helidon WebServer. Generates optimized routing code from standard JAX-RS annotations at compile time.

## Features

- Standard JAX-RS annotations (`@Path`, `@GET`, `@POST`, etc.)
- Build-time code generation via Annotation Processing Tool (APT)
- Direct method calls - no runtime reflection
- GraalVM native-image compatible
- Container filters (`ContainerRequestFilter`, `ContainerResponseFilter`)
- Reader/Writer interceptors (`ReaderInterceptor`, `WriterInterceptor`)
- Custom exception mappers (`ExceptionMapper`)
- Filter ordering with `@Priority` and `@NameBinding`

## Supported JAX-RS Features

### HTTP Methods
- `@GET`, `@POST`, `@PUT`, `@DELETE`, `@PATCH`, `@HEAD`, `@OPTIONS`

### Parameters
- `@PathParam` - Path parameters (`/users/{id}`)
- `@QueryParam` - Query string parameters (`?name=value`)
- `@HeaderParam` - HTTP headers
- `@CookieParam` - Cookies
- `@FormParam` - Form data
- `@MatrixParam` - Matrix parameters (`/path;param=value`)
- `@DefaultValue` - Default parameter values
- `@BeanParam` - Aggregate multiple parameters into a bean
- `List<T>` / `Set<T>` - Multiple values for query/header params

### Return Types
- POJO classes (serialized to JSON)
- `jakarta.ws.rs.core.Response` - Full control over status, headers, entity
- `void` - Returns 204 No Content

### Content Types & Negotiation
- `@Produces` - Response content type (validates Accept header, returns 406 if not acceptable)
- `@Consumes` - Request content type (validates Content-Type header, returns 415 if unsupported)
- Wildcard media types supported (`text/*`, `application/*`, `*/*`)

### Context Injection
- `@Context UriInfo` - URI information
- `@Context HttpHeaders` - HTTP headers

### Filters and Interceptors
- `ContainerRequestFilter` - Pre-processing filter
- `ContainerResponseFilter` - Post-processing filter
- `ReaderInterceptor` - Request body interceptor
- `WriterInterceptor` - Response body interceptor
- `@PreMatching` - Pre-matching request filters
- `@Priority` - Filter/interceptor ordering
- `@NameBinding` - Selective filter application

### Exception Handling
- `ExceptionMapper<T>` - Custom exception to response mapping
- Built-in handling for JAX-RS exceptions (NotFoundException, BadRequestException, etc.)

## Quick Start

### 1. Write JAX-RS Resource

```java
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {

    @GET
    @Path("/{id}")
    public User getUser(@PathParam("id") Long id) {
        return users.get(id);
    }

    @POST
    public User createUser(User user) {
        // ...
    }

    @GET
    @Path("/search")
    public List<User> search(@QueryParam("tag") List<String> tags,
                             @QueryParam("limit") @DefaultValue("10") Integer limit) {
        // Supports multiple query param values
    }

    @DELETE
    @Path("/{id}")
    public Response deleteUser(@PathParam("id") Long id) {
        users.remove(id);
        return Response.noContent().build();
    }
}
```

### 2. Compile

```bash
mvn clean compile
```

The annotation processor generates `UserResource$$JaxRsRouting`:

```
[INFO] Generating routing for: io.helidon.examples.jaxrs.apt.UserResource
[INFO] Generated: io.helidon.examples.jaxrs.apt.UserResource$$JaxRsRouting
```

### 3. Register Routes

```java
public class Main {
    public static void main(String[] args) {
        WebServer server = WebServer.builder()
                .port(8080)
                .routing(routing ->
                    new UserResource$$JaxRsRouting().register(routing))
                .build()
                .start();
    }
}
```

### 4. Run

```bash
mvn package
java -cp "target/helidon-examples-jaxrs-apt.jar;target/libs/*" io.helidon.examples.jaxrs.apt.Main
```

### 5. Test

```bash
curl http://localhost:8080/users
curl http://localhost:8080/users/1
curl -X POST http://localhost:8080/users -H "Content-Type: application/json" -d '{"name":"Alice"}'
```

## Advanced Features

### @BeanParam - Parameter Aggregation

Aggregate multiple parameters into a single bean:

```java
public class SearchParams {
    @QueryParam("q")
    private String query;

    @QueryParam("page")
    @DefaultValue("1")
    private Integer page;

    @HeaderParam("X-Sort-Order")
    @DefaultValue("asc")
    private String sortOrder;

    // getters and setters
}

@Path("/search")
public class SearchResource {
    @GET
    public List<Result> search(@BeanParam SearchParams params) {
        // params.getQuery(), params.getPage(), params.getSortOrder()
    }
}
```

### List/Set Parameters

Handle multiple values for the same parameter:

```java
@GET
@Path("/filter")
public List<Item> filter(@QueryParam("tag") List<String> tags,
                         @QueryParam("id") Set<Long> ids) {
    // tags = ["java", "kotlin"] from ?tag=java&tag=kotlin
    // ids = Set of unique IDs
}
```

### Matrix Parameters

Matrix parameters are embedded in path segments:

```java
@GET
@Path("/products/{id}")
public Product getProduct(@PathParam("id") Long id,
                          @MatrixParam("color") String color,
                          @MatrixParam("size") @DefaultValue("M") String size) {
    // URL: /products/123;color=red;size=L
    // id = 123, color = "red", size = "L"
}

@GET
@Path("/filter")
public List<Item> filter(@MatrixParam("status") String status,
                         @MatrixParam("limit") @DefaultValue("20") Integer limit) {
    // URL: /filter;status=active;limit=10
}
```

### Response Return Type

Full control over HTTP response:

```java
@POST
public Response createUser(User user) {
    User created = service.save(user);
    return Response.status(201)
            .header("X-Created-Id", created.getId())
            .entity(created)
            .build();
}

@DELETE
@Path("/{id}")
public Response delete(@PathParam("id") Long id) {
    service.delete(id);
    return Response.noContent().build();
}
```

### Custom Exception Mappers

Map exceptions to HTTP responses:

```java
@Provider
public class BusinessExceptionMapper implements ExceptionMapper<BusinessException> {
    @Override
    public Response toResponse(BusinessException e) {
        return Response.status(422)
                .header("X-Error-Code", e.getErrorCode())
                .entity(new ErrorResponse(e.getMessage()))
                .build();
    }
}
```

### Filters with Priority and Name Binding

```java
// Global filter with priority (lower = executes first for requests)
@Provider
@Priority(100)
public class AuthFilter implements ContainerRequestFilter {
    @Override
    public void filter(ContainerRequestContext ctx) {
        if (!isAuthenticated(ctx)) {
            ctx.abortWith(Response.status(401).build());
        }
    }
}

// Name-bound filter (only applies to annotated methods)
@NameBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Logged {}

@Provider
@Logged
public class LoggingFilter implements ContainerRequestFilter {
    // Only applies to @Logged methods
}

@Path("/api")
public class ApiResource {
    @GET
    @Logged  // LoggingFilter applies here
    public String getData() { ... }
}
```

### Reader/Writer Interceptors

```java
@Provider
@Priority(100)
public class GzipInterceptor implements ReaderInterceptor, WriterInterceptor {
    @Override
    public Object aroundReadFrom(ReaderInterceptorContext ctx) throws IOException {
        // Decompress request body
        return ctx.proceed();
    }

    @Override
    public void aroundWriteTo(WriterInterceptorContext ctx) throws IOException {
        // Compress response body
        ctx.proceed();
    }
}
```

## Architecture

```
+-----------------------------------------------------+
|              JAX-RS Resource Classes                |
|  @Path("/users") class UserResource { ... }         |
+-----------------------------------------------------+
                       |
                       | Compile time
                       v
+-----------------------------------------------------+
|          Annotation Processor (APT)                 |
|  JaxRsProcessor scans @Path, @Provider classes      |
|  - Collects filters, interceptors, exception mappers|
|  - Generates optimized routing code                 |
+-----------------------------------------------------+
                       |
                       | Generates
                       v
+-----------------------------------------------------+
|       Generated Routing Classes                     |
|  UserResource$$JaxRsRouting                         |
|  - Direct method calls                              |
|  - Type-safe parameter extraction                   |
|  - Filter/interceptor chain execution               |
|  - Exception mapper lookup                          |
+-----------------------------------------------------+
                       |
                       | Runtime
                       v
+-----------------------------------------------------+
|         Helidon WebServer (Virtual Threads)         |
|  Request -> Filters -> Handler -> Filters -> Response|
+-----------------------------------------------------+
```

## Project Structure

```
src/main/java/io/helidon/examples/jaxrs/apt/
├── Main.java                 # Application entry point
├── User.java                 # Domain model
├── UserResource.java         # JAX-RS resource
├── filter/
│   └── LoggingFilter.java    # Example filter
├── processor/
│   └── JaxRsProcessor.java   # Annotation processor
└── runtime/
    ├── Generated.java                       # Marker annotation
    ├── FilterContext.java                   # Filter/interceptor/mapper registry
    ├── SimpleRuntimeDelegate.java           # JAX-RS RuntimeDelegate impl
    ├── HelidonUriInfo.java                  # UriInfo implementation
    ├── HelidonHttpHeaders.java              # HttpHeaders implementation
    ├── HelidonContainerRequestContext.java  # Request filter context
    ├── HelidonContainerResponseContext.java # Response filter context
    ├── HelidonReaderInterceptorContext.java # Reader interceptor context
    └── HelidonWriterInterceptorContext.java # Writer interceptor context
```

## Testing

Run all tests:
```bash
mvn test
```

Run integration tests only:
```bash
mvn test -Pintegration-tests
```

The project includes 134 tests covering:
- Parameter extraction (path, query, header, cookie, form, matrix, bean)
- Collection parameters (List/Set)
- Content negotiation (@Consumes, @Produces, Accept header validation)
- Response return types
- Filter ordering and execution
- Interceptor chains
- Exception mappers
- CRUD operations

## Requirements

- Java 21+
- Maven 3.8+
- Helidon 4.x

## License

Apache License 2.0
