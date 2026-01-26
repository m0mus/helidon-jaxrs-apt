# Quick Start Guide

## 1. Build

```bash
mvn clean compile
```

Watch for annotation processor output:
```
[INFO] Found request filter: io.helidon.examples.jaxrs.apt.filter.LoggingFilter (priority=1000)
[INFO] Found response filter: io.helidon.examples.jaxrs.apt.filter.LoggingFilter (priority=1000)
[INFO] Generating routing for: io.helidon.examples.jaxrs.apt.UserResource
[INFO] Generated: io.helidon.examples.jaxrs.apt.UserResource$$JaxRsRouting
```

## 2. View Generated Code

```bash
cat target/generated-sources/annotations/io/helidon/examples/jaxrs/apt/UserResource$$JaxRsRouting.java
```

## 3. Package

```bash
mvn package
```

## 4. Run

```bash
java -cp "target/helidon-examples-jaxrs-apt.jar;target/libs/*" io.helidon.examples.jaxrs.apt.Main
```

Output:
```
Server started at http://localhost:8080
```

## 5. Test the API

```bash
# List all users
curl http://localhost:8080/users

# Get user by ID
curl http://localhost:8080/users/1

# Create a new user
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Charlie","email":"charlie@example.com"}'

# Update a user
curl -X PUT http://localhost:8080/users/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice Updated","email":"alice@example.com"}'

# Delete a user
curl -X DELETE http://localhost:8080/users/2

# Filter users by name
curl "http://localhost:8080/users?name=Alice"

# Count users
curl http://localhost:8080/users/count
```

## 6. Run Tests

```bash
# Run all tests (94 tests)
mvn test

# Run only integration tests
mvn test -Pintegration-tests

# Run specific test class
mvn test -Dtest=ParameterExtractionTest

# Run with verbose output
mvn test -X
```

## Adding New Resources

1. Create a JAX-RS resource class:

```java
@Path("/products")
@Produces(MediaType.APPLICATION_JSON)
public class ProductResource {

    @GET
    public List<Product> listProducts() {
        return productService.findAll();
    }

    @GET
    @Path("/{id}")
    public Product getProduct(@PathParam("id") Long id) {
        return productService.findById(id);
    }

    @POST
    public Response createProduct(Product product) {
        Product saved = productService.save(product);
        return Response.status(201).entity(saved).build();
    }

    @GET
    @Path("/search")
    public List<Product> search(@QueryParam("tag") List<String> tags,
                                @BeanParam SearchParams params) {
        // Supports List parameters and @BeanParam
    }
}
```

2. Compile (generates `ProductResource$$JaxRsRouting`):

```bash
mvn compile
```

3. Register in Main:

```java
new UserResource$$JaxRsRouting().register(routing);
new ProductResource$$JaxRsRouting().register(routing);
```

## Adding Custom Exception Mapper

1. Create the exception:

```java
public class ProductNotFoundException extends RuntimeException {
    private final Long productId;

    public ProductNotFoundException(Long productId) {
        super("Product not found: " + productId);
        this.productId = productId;
    }

    public Long getProductId() { return productId; }
}
```

2. Create the mapper:

```java
@Provider
public class ProductNotFoundMapper implements ExceptionMapper<ProductNotFoundException> {
    @Override
    public Response toResponse(ProductNotFoundException e) {
        return Response.status(404)
                .entity(Map.of("error", e.getMessage(), "productId", e.getProductId()))
                .build();
    }
}
```

3. Throw from resource (mapper is auto-detected):

```java
@GET
@Path("/{id}")
public Product getProduct(@PathParam("id") Long id) {
    return productService.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
}
```

## Adding Filters

1. Create a filter with `@Provider`:

```java
@Provider
@Priority(100)  // Lower = executes first for requests
public class AuthFilter implements ContainerRequestFilter {
    @Override
    public void filter(ContainerRequestContext ctx) {
        String auth = ctx.getHeaderString("Authorization");
        if (auth == null || !isValid(auth)) {
            ctx.abortWith(Response.status(401)
                    .entity("Unauthorized")
                    .build());
        }
    }
}
```

2. Compile and run - the filter is auto-detected and applied to all routes.

## Troubleshooting

### Generated Code Not Found

Run `mvn clean compile` first. The routing class is generated during compilation.

### Annotation Processor Not Running

Check that the processor is registered in:
```
src/main/resources/META-INF/services/javax.annotation.processing.Processor
```

Should contain:
```
io.helidon.examples.jaxrs.apt.processor.JaxRsProcessor
```

### Filter Not Being Applied

Ensure the filter class has:
- `@Provider` annotation
- Implements `ContainerRequestFilter` or `ContainerResponseFilter`
- Is in a package that's compiled during the processor phase

### ExceptionMapper Not Working

Ensure:
- `@Provider` annotation is present
- Class implements `ExceptionMapper<YourException>`
- `SimpleRuntimeDelegate.init()` is called (happens automatically in generated code)
