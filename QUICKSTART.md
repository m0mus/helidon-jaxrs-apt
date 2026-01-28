# Quickstart

This module provides filter-only JAX-RS support for Helidon WebServer.

## 1) Add JAX-RS filters

Create `ContainerRequestFilter` and/or `ContainerResponseFilter` implementations
and register them via ServiceLoader:

`src/main/resources/META-INF/services/jakarta.ws.rs.container.ContainerRequestFilter`

`src/main/resources/META-INF/services/jakarta.ws.rs.container.ContainerResponseFilter`

## 2) Register the feature

```
HttpRouting.builder()
        .addFeature(JaxRsFilterFeature::new)
        .build();
```

## 3) Run tests

```
mvn test
```
