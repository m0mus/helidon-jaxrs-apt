package io.helidon.examples.jaxrs.apt.test.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

/**
 * Pre-matching filter that handles X-HTTP-Method-Override header.
 * Allows clients to override the HTTP method, useful for proxies that only support GET/POST.
 */
@Provider
@PreMatching
@Priority(50)  // Run early
public class MethodOverrideFilter implements ContainerRequestFilter {

    public static final String METHOD_OVERRIDE_HEADER = "X-HTTP-Method-Override";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // Only process POST requests with override header
        if ("POST".equals(requestContext.getMethod())) {
            String methodOverride = requestContext.getHeaderString(METHOD_OVERRIDE_HEADER);
            if (methodOverride != null && !methodOverride.isEmpty()) {
                // Override the method
                requestContext.setMethod(methodOverride.toUpperCase());
            }
        }
    }
}
