# Helidon JAX-RS APT

Build-time JAX-RS annotation processing for Helidon WebServer. Generates optimized routing code from standard JAX-RS annotations at compile time.

## Features

- Standard JAX-RS annotations (`@Path`, `@GET`, `@POST`, etc.)
- Build-time code generation via Annotation Processing Tool (APT)
- Direct method calls - no runtime reflection
- GraalVM native-image compatible
- Container filters (`ContainerRequestFilter`, `ContainerResponseFilter`)

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

## Supported JAX-RS Annotations

### HTTP Methods
- `@GET`, `@POST`, `@PUT`, `@DELETE`, `@PATCH`, `@HEAD`, `@OPTIONS`

### Parameters
- `@PathParam` - Path parameters (`/users/{id}`)
- `@QueryParam` - Query string parameters (`?name=value`)
- `@HeaderParam` - HTTP headers
- `@CookieParam` - Cookies
- `@FormParam` - Form data
- `@DefaultValue` - Default parameter values

### Content Types
- `@Produces` - Response content type
- `@Consumes` - Request content type

### Context Injection
- `@Context UriInfo` - URI information
- `@Context HttpHeaders` - HTTP headers

### Filters
- `ContainerRequestFilter` - Pre-processing filter
- `ContainerResponseFilter` - Post-processing filter

Mark filters with `@Provider` annotation:

```java
@Provider
public class LoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) {
        // Called before resource method
    }

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) {
        // Called after resource method
    }
}
```

## Generated Code

For this resource:

```java
@Path("/users")
public class UserResource {
    @GET
    @Path("/{id}")
    public User getUser(@PathParam("id") Long id) {
        return users.get(id);
    }
}
```

The processor generates:

```java
@Generated("io.helidon.examples.jaxrs.apt.processor.JaxRsProcessor")
public final class UserResource$$JaxRsRouting {

    private final UserResource resource;
    private final ObjectMapper objectMapper;
    private final List<ContainerRequestFilter> requestFilters;
    private final List<ContainerResponseFilter> responseFilters;

    public void register(HttpRouting.Builder routing) {
        routing.get("/users/{id}", this::getUser_handler);
    }

    private void getUser_handler(ServerRequest req, ServerResponse res) {
        try {
            // Run request filters
            HelidonContainerRequestContext requestContext = new HelidonContainerRequestContext(req);
            for (var filter : requestFilters) {
                filter.filter(requestContext);
                if (requestContext.isAborted()) {
                    res.status(requestContext.getAbortStatus()).send(requestContext.getAbortMessage());
                    return;
                }
            }

            // Extract parameters and call resource method
            Long id = Long.parseLong(req.path().pathParameters().get("id"));
            User result = resource.getUser(id);

            // Run response filters
            HelidonContainerResponseContext responseContext = new HelidonContainerResponseContext(200, result);
            for (var filter : responseFilters) {
                filter.filter(requestContext, responseContext);
            }

            // Send response
            String json = objectMapper.writeValueAsString(responseContext.getEntity());
            res.status(responseContext.getStatus()).header("Content-Type", "application/json").send(json);
        } catch (NotFoundException e) {
            res.status(404).send(e.getMessage());
        } catch (WebApplicationException e) {
            res.status(e.getResponse().getStatus()).send(e.getMessage());
        } catch (Exception e) {
            res.status(500).send("Internal Server Error: " + e.getMessage());
        }
    }
}
```

## Architecture

```
┌─────────────────────────────────────────────────────┐
│              JAX-RS Resource Classes                │
│  @Path("/users") class UserResource { ... }         │
└─────────────────────────────────────────────────────┘
                       │
                       │ Compile time
                       ▼
┌─────────────────────────────────────────────────────┐
│          Annotation Processor (APT)                 │
│  JaxRsProcessor scans @Path, @Provider classes      │
│  Generates optimized routing code                   │
└─────────────────────────────────────────────────────┘
                       │
                       │ Generates
                       ▼
┌─────────────────────────────────────────────────────┐
│       Generated Routing Classes                     │
│  UserResource$$JaxRsRouting                         │
│  - Direct method calls                              │
│  - Type-safe parameter extraction                   │
│  - Filter chain execution                           │
└─────────────────────────────────────────────────────┘
                       │
                       │ Runtime
                       ▼
┌─────────────────────────────────────────────────────┐
│         Helidon WebServer (Virtual Threads)         │
│  Request → Filters → Handler → Filters → Response   │
└─────────────────────────────────────────────────────┘
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
    ├── Generated.java                      # Marker annotation
    ├── HelidonUriInfo.java                 # UriInfo implementation
    ├── HelidonHttpHeaders.java             # HttpHeaders implementation
    ├── HelidonContainerRequestContext.java # Request filter context
    └── HelidonContainerResponseContext.java# Response filter context
```

## Requirements

- Java 21+
- Maven 3.8+
- Helidon 4.x

## License

Apache License 2.0
