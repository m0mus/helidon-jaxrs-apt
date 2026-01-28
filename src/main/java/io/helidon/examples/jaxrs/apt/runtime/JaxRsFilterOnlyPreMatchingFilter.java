package io.helidon.examples.jaxrs.apt.runtime;

import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import jakarta.ws.rs.container.ContainerRequestFilter;

import java.util.List;
import java.util.Objects;

/**
 * Pre-matching filter wrapper that is disabled when JAX-RS routing is present.
 */
public class JaxRsFilterOnlyPreMatchingFilter implements Filter {

    private final JaxRsPreMatchingFilter delegate;

    /**
     * Create a new wrapper around pre-matching filters.
     *
     * @param filters pre-matching filters to execute
     */
    public JaxRsFilterOnlyPreMatchingFilter(List<ContainerRequestFilter> filters) {
        Objects.requireNonNull(filters, "filters");
        this.delegate = new JaxRsPreMatchingFilter(filters);
    }

    @Override
    public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
        if (JaxRsFilterSupport.isJaxRsRoutingRegistered()) {
            chain.proceed();
            return;
        }
        delegate.filter(chain, req, res);
    }
}
