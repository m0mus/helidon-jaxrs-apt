# Technical Notes

## Architecture

The runtime is intentionally small and focused on filter-only execution:

- `JaxRsFilterSupport` loads `ContainerRequestFilter` and
  `ContainerResponseFilter` implementations via `ServiceLoader`.
- `JaxRsContextFilter` registers `UriInfo`, `HttpHeaders`, and
  `SecurityContext` in the Helidon request context.
- `JaxRsPreMatchingFilter` runs `@PreMatching` filters before routing.
- `JaxRsFilter` wraps post-matching request/response filters. It
  implements `HttpEntryPoint.Interceptor` and is registered as a Helidon
  filter so it runs for every route.
- `SimpleRuntimeDelegate` supplies minimal JAX-RS header parsing and
  response builder support for filters only.

## Context injection

Filters that use `@Context` receive proxy implementations:

- `UriInfoProxy`
- `HttpHeadersProxy`
- `SecurityContextProxy`
- `ResourceInfoProxy`

These proxies resolve request-scoped objects from Helidon context at call time.

## Ordering

`@Priority` is honored. Lower values run earlier for request filters and
pre-matching filters. Response filters run in reverse order, matching the
JAX-RS contract.

## Name bindings

Name bindings are discovered from `@NameBinding` annotations, but without
resources there is no matching context. Name-bound filters are registered but
will not run in filter-only mode.

## Compatibility

This module is intended for Helidon WebServer only and does not target
coexistence with a full JAX-RS runtime.

## Limitations

This module does not include:

- JAX-RS resources or routing
- Annotation processing
- Interceptors or exception mappers
- JAX-RS parameter extraction

