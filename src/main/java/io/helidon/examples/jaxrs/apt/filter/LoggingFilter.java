package io.helidon.examples.jaxrs.apt.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Example logging filter that logs requests and responses.
 * Priority 1000 ensures it runs early in the filter chain.
 */
@Provider
@Priority(1000)
public class LoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOGGER = Logger.getLogger(LoggingFilter.class.getName());

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        LOGGER.info(() -> String.format("[REQUEST] %s %s",
                requestContext.getMethod(),
                requestContext.getUriInfo().getPath()));
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        LOGGER.info(() -> String.format("[RESPONSE] %s %s -> %d",
                requestContext.getMethod(),
                requestContext.getUriInfo().getPath(),
                responseContext.getStatus()));
    }
}
