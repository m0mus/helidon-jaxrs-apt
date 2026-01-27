package io.helidon.examples.jaxrs.apt.runtime;

import io.helidon.http.HttpPrologue;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

/**
 * Helidon Filter that wraps JAX-RS @PreMatching ContainerRequestFilters.
 *
 * <p>This filter runs BEFORE routing, allowing JAX-RS pre-matching filters to:
 * <ul>
 *   <li>Modify the request URI (affecting which route is matched)</li>
 *   <li>Modify the HTTP method</li>
 *   <li>Modify request headers</li>
 *   <li>Abort the request before routing occurs</li>
 * </ul>
 *
 * <p>Filters are executed in priority order (lower priority value = earlier execution).
 */
public class JaxRsPreMatchingFilter implements Filter {

    private final List<ContainerRequestFilter> filters;

    /**
     * Create a new pre-matching filter wrapper.
     *
     * @param filters the JAX-RS pre-matching filters to execute, already sorted by priority
     */
    public JaxRsPreMatchingFilter(List<ContainerRequestFilter> filters) {
        this.filters = filters;
    }

    @Override
    public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
        if (filters.isEmpty()) {
            chain.proceed();
            return;
        }

        // Create a mutable request context that tracks modifications
        PreMatchingRequestContext ctx = new PreMatchingRequestContext(req);

        try {
            for (ContainerRequestFilter filter : filters) {
                filter.filter(ctx);

                if (ctx.isAborted()) {
                    // Send abort response and don't proceed to routing
                    sendAbortResponse(res, ctx);
                    return;
                }
            }

            // Apply any URI/method modifications back to the request
            if (ctx.isModified()) {
                HttpPrologue newPrologue = ctx.buildModifiedPrologue();
                req.prologue(newPrologue);
            }

            // Continue to routing
            chain.proceed();

        } catch (Exception e) {
            // Convert to runtime exception - Helidon will handle it
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Pre-matching filter failed", e);
        }
    }

    private void sendAbortResponse(RoutingResponse res, PreMatchingRequestContext ctx) {
        Response abortResponse = ctx.getAbortResponse();
        int status = abortResponse.getStatus();

        // Set headers from abort response
        for (Map.Entry<String, List<Object>> entry : abortResponse.getHeaders().entrySet()) {
            String headerName = entry.getKey();
            for (Object value : entry.getValue()) {
                res.header(io.helidon.http.HeaderNames.create(headerName), String.valueOf(value));
            }
        }

        // Send response
        Object entity = abortResponse.getEntity();
        if (entity != null) {
            if (entity instanceof String) {
                res.status(status).send((String) entity);
            } else {
                res.status(status).send(entity.toString());
            }
        } else {
            res.status(status).send();
        }
    }
}
