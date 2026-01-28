# Helidon JAX-RS Filter Support

This project runs JAX-RS `ContainerRequestFilter` and `ContainerResponseFilter`
in Helidon WebServer without any JAX-RS resources or runtime.

Packages live under `io.helidon.jaxrs.filters`.

## Dependency

Maven coordinates:

```xml
<dependency>
    <groupId>io.helidon.jaxrs</groupId>
    <artifactId>helidon-jaxrs-filters</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## What this module supports

- ServiceLoader discovery of request and response filters
- `@PreMatching` filters executed as Helidon WebServer filters
- Post-matching filters executed through an `HttpEntryPoint.Interceptor`
  (implemented by `JaxRsFilter`)
- `@Context` proxies for `UriInfo`, `HttpHeaders`, `SecurityContext`,
  and `ResourceInfo`
- `@Priority` ordering and `@NameBinding` recognition
- Entity stream manipulation via `setEntityStream()` in both pre-matching and post-matching filters
- Entity replacement via `setEntity()` in response filters

## What this module does not support

- JAX-RS resources, annotation processing, or runtime routing
- Reader/writer interceptors or exception mappers
- Parameter injection, validation, or content negotiation helpers

## Usage

1) Register your filters using ServiceLoader.

`src/main/resources/META-INF/services/jakarta.ws.rs.container.ContainerRequestFilter`:

```
com.example.filters.LoggingRequestFilter
com.example.filters.PreMatchingRewriteFilter
```

`src/main/resources/META-INF/services/jakarta.ws.rs.container.ContainerResponseFilter`:

```
com.example.filters.LoggingResponseFilter
```

2) Register the feature in Helidon routing:

```
import io.helidon.jaxrs.filters.JaxRsFilterFeature;

HttpRouting.builder()
        .addFeature(JaxRsFilterFeature::new)
        .build();
```

You can also call `JaxRsFilterSupport.register(routing)` directly.

## Pre-matching vs post-matching

- Pre-matching: implement `@PreMatching` on a `ContainerRequestFilter`. These
  run before routing and can update the request URI or method.
- Post-matching: all non-pre-matching request filters and response filters run
  through `JaxRsFilter`, which also implements
  `HttpEntryPoint.Interceptor`.

## Behavior notes

- `ResourceInfo` is present but carries no matched resource metadata.
- `abortWith(...)` on a request filter short-circuits routing.
- Response filters can modify status, headers, and entity before send.
- Request filters can replace the entity stream using `setEntityStream()` for content transformation.

## Name bindings

Name-bound filters are discovered and registered, but without JAX-RS resources
there are no binding annotations to match. As a result, name-bound filters do
not execute in filter-only mode.

## Compatibility

This module is designed for Helidon WebServer usage only and does not
coexist with a full JAX-RS runtime in the same JVM.

## Tests

Run:

```
mvn test
```

