package io.helidon.examples.jaxrs.apt.test.filter;

import io.helidon.examples.jaxrs.apt.test.util.FilterOrderTracker;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

/**
 * Response filter that throws when explicitly enabled for error responses.
 */
@Provider
@Priority(900)
public class ThrowingResponseFilter implements ContainerResponseFilter {

    public static final String THROW_HEADER = "X-Throw-Response-Filter";

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        if (requestContext.getHeaderString(THROW_HEADER) != null && responseContext.getStatus() >= 400) {
            FilterOrderTracker.recordResponseFilter("ThrowingResponseFilter");
            throw new IOException("Response filter error");
        }
    }
}
