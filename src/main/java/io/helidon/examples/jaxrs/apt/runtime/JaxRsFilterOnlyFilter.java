package io.helidon.examples.jaxrs.apt.runtime;

import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Filter that runs post-matching JAX-RS filters without JAX-RS resources.
 */
public class JaxRsFilterOnlyFilter implements Filter {

    private static final Set<String> NO_BINDINGS = Set.of();
    private static final ResourceInfo NO_RESOURCE_INFO = new NoResourceInfo();

    private final FilterContext filterContext;

    /**
     * Create a new filter-only wrapper.
     *
     * @param filterContext filter registry for request/response filters
     */
    public JaxRsFilterOnlyFilter(FilterContext filterContext) {
        this.filterContext = Objects.requireNonNull(filterContext, "filterContext");
    }

    @Override
    public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
        if (filterContext.getRequestFiltersWithBindings().isEmpty()
                && filterContext.getResponseFiltersWithBindings().isEmpty()) {
            chain.proceed();
            return;
        }

        req.context().register(ResourceInfo.class, NO_RESOURCE_INFO);
        HelidonContainerRequestContext requestContext =
                new HelidonContainerRequestContext(req, NO_RESOURCE_INFO);

        for (FilterContext.FilterEntry<ContainerRequestFilter> entry
                : filterContext.getRequestFiltersWithBindings()) {
            if (entry.matches(NO_BINDINGS)) {
                try {
                    entry.filter().filter(requestContext);
                } catch (IOException ex) {
                    throw new RuntimeException("Request filter failed", ex);
                }
                if (requestContext.isAborted()) {
                    sendAbortResponse(res, requestContext);
                    return;
                }
            }
        }

        res.beforeSend(() -> {
            HelidonContainerResponseContext responseContext =
                    new HelidonContainerResponseContext(res.status().code(), null);
            copyExistingHeaders(res, responseContext);
            try {
                runResponseFilters(requestContext, responseContext);
            } catch (IOException ex) {
                throw new RuntimeException("Response filter failed", ex);
            }
            res.status(responseContext.getStatus());
            res.headers().clear();
            copyResponseHeaders(res, responseContext);
        });

        chain.proceed();
    }

    private void runResponseFilters(HelidonContainerRequestContext requestContext,
                                    HelidonContainerResponseContext responseContext) throws IOException {
        for (FilterContext.FilterEntry<ContainerResponseFilter> entry
                : filterContext.getResponseFiltersWithBindings()) {
            if (entry.matches(NO_BINDINGS)) {
                entry.filter().filter(requestContext, responseContext);
            }
        }
    }

    private void copyExistingHeaders(RoutingResponse res, HelidonContainerResponseContext responseContext) {
        for (Map.Entry<String, List<String>> entry : res.headers().toMap().entrySet()) {
            for (String value : entry.getValue()) {
                if (value != null) {
                    responseContext.getHeaders().add(entry.getKey(), value);
                    responseContext.getStringHeaders().add(entry.getKey(), value);
                }
            }
        }
    }

    private void copyResponseHeaders(RoutingResponse res, HelidonContainerResponseContext responseContext) {
        for (var entry : responseContext.getHeaders().entrySet()) {
            for (var value : entry.getValue()) {
                if (value != null) {
                    res.header(io.helidon.http.HeaderNames.create(entry.getKey()), value.toString());
                }
            }
        }
    }

    private void sendAbortResponse(RoutingResponse res, HelidonContainerRequestContext requestContext) {
        String msg = requestContext.getAbortMessage();
        res.status(requestContext.getAbortStatus()).send(msg != null ? msg : "");
    }

    private static final class NoResourceInfo implements ResourceInfo {
        @Override
        public Method getResourceMethod() {
            return null;
        }

        @Override
        public Class<?> getResourceClass() {
            return null;
        }
    }
}
