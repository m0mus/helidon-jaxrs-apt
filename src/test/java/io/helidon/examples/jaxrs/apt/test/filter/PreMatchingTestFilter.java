package io.helidon.examples.jaxrs.apt.test.filter;

import io.helidon.examples.jaxrs.apt.test.util.FilterOrderTracker;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

/**
 * Pre-matching filter that executes before route matching.
 * Pre-matching filters can modify the request before routing decisions are made.
 */
@Provider
@PreMatching
@Priority(50)
public class PreMatchingTestFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        FilterOrderTracker.recordRequestFilter("PreMatchingTestFilter");
    }
}
