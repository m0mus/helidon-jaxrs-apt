package io.helidon.examples.jaxrs.apt.test.filter;

import io.helidon.examples.jaxrs.apt.test.util.FilterOrderTracker;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

/**
 * Post-matching filter that aborts requests to the /filter/abort endpoint.
 */
@Provider
@Priority(150)
public class AbortFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();
        if ("filter/abort".equals(path) || "/filter/abort".equals(path)) {
            FilterOrderTracker.recordRequestFilter("AbortFilter");
            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                    .entity("Request aborted by AbortFilter")
                    .build());
        }
    }
}
