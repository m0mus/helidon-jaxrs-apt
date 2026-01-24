# Quick Start Guide

## 1. Build

```bash
mvn clean compile
```

Watch for annotation processor output:
```
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

## 5. Test

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

    @POST
    public Product createProduct(Product product) {
        return productService.save(product);
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
