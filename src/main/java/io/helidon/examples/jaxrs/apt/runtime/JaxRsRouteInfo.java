package io.helidon.examples.jaxrs.apt.runtime;

import jakarta.ws.rs.container.ResourceInfo;

import java.util.Set;

/**
 * Route metadata for post-matching filter execution.
 *
 * @param resourceInfo matched resource class and method
 * @param methodBindings name bindings applied to the matched resource method
 */
public record JaxRsRouteInfo(ResourceInfo resourceInfo, Set<String> methodBindings) {
}
