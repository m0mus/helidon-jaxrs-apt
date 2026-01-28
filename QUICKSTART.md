# Quickstart

This module provides filter-only JAX-RS support for Helidon WebServer.

Core API package: `io.helidon.jaxrs.filters`.

## 0) Add the dependency

```xml
<dependency>
    <groupId>io.helidon.jaxrs</groupId>
    <artifactId>helidon-jaxrs-filters</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## 1) Add JAX-RS filters

Create `ContainerRequestFilter` and/or `ContainerResponseFilter` implementations
and register them via ServiceLoader:

`src/main/resources/META-INF/services/jakarta.ws.rs.container.ContainerRequestFilter`

`src/main/resources/META-INF/services/jakarta.ws.rs.container.ContainerResponseFilter`

## 2) Register the feature

```
import io.helidon.jaxrs.filters.JaxRsFilterFeature;

HttpRouting.builder()
        .addFeature(JaxRsFilterFeature::new)
        .build();
```

## 3) Run tests

```
mvn test
```
