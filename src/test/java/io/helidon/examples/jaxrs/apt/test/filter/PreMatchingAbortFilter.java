package io.helidon.examples.jaxrs.apt.test.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

/**
 * Pre-matching filter that aborts requests based on headers.
 * Demonstrates that pre-matching filters can abort before routing occurs.
 */
@Provider
@PreMatching
@Priority(10)  // Run very early
public class PreMatchingAbortFilter implements ContainerRequestFilter {

    public static final String ABORT_HEADER = "X-Abort-Request";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String abortValue = requestContext.getHeaderString(ABORT_HEADER);
        if (abortValue != null) {
            // Abort the request before routing
            requestContext.abortWith(Response
                    .status(Response.Status.FORBIDDEN)
                    .entity("Request aborted by pre-matching filter: " + abortValue)
                    .build());
        }
    }
}
