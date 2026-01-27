package io.helidon.examples.jaxrs.apt.test.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.net.URI;

/**
 * Pre-matching filter that rewrites URIs.
 * Demonstrates that pre-matching filters can modify the request URI before routing.
 */
@Provider
@PreMatching
@Priority(100)
public class UriRewritingFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();

        // Rewrite /legacy/resource to /filter/test
        if (path.equals("legacy/resource") || path.equals("/legacy/resource")) {
            requestContext.setRequestUri(URI.create("/filter/test"));
        }

        // Rewrite /v1/items to /filter/test with query param
        if (path.equals("v1/items") || path.equals("/v1/items")) {
            requestContext.setRequestUri(URI.create("/filter/test?rewritten=true"));
        }
    }
}
