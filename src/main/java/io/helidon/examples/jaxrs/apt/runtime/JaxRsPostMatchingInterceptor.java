package io.helidon.examples.jaxrs.apt.runtime;

import io.helidon.service.registry.InterceptionContext;
import io.helidon.webserver.http.HttpEntryPoint;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

/**
 * Post-matching filter execution implemented as an HttpEntryPoint interceptor.
 */
public class JaxRsPostMatchingInterceptor implements HttpEntryPoint.Interceptor {

    private final FilterContext filterContext;

    /**
     * Create a new interceptor backed by the provided filter context.
     *
     * @param filterContext filter registry for request/response filters
     */
    public JaxRsPostMatchingInterceptor(FilterContext filterContext) {
        this.filterContext = Objects.requireNonNull(filterContext, "filterContext");
    }

    @Override
    public void proceed(InterceptionContext ctx,
                        Chain chain,
                        ServerRequest req,
                        ServerResponse res) throws Exception {
        JaxRsRouteInfo routeInfo = req.context()
                .get(JaxRsRouteInfo.class, JaxRsRouteInfo.class)
                .orElse(null);
        if (routeInfo == null) {
            chain.proceed(req, res);
            return;
        }

        req.context().register(ResourceInfo.class, routeInfo.resourceInfo());

        Set<String> methodBindings = routeInfo.methodBindings();
        if (methodBindings == null) {
            methodBindings = Set.of();
        }

        HelidonContainerRequestContext requestContext =
                new HelidonContainerRequestContext(req, routeInfo.resourceInfo());

        for (FilterContext.FilterEntry<ContainerRequestFilter> entry
                : filterContext.getRequestFiltersWithBindings()) {
            if (entry.matches(methodBindings)) {
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

        chain.proceed(req, res);

        JaxRsResponseData responseData = req.context()
                .get(JaxRsResponseData.class, JaxRsResponseData.class)
                .orElse(null);
        if (responseData == null) {
            return;
        }

        HelidonContainerResponseContext responseContext = responseData.responseContext();

        if (responseData.applyResponseFilters()) {
            if (responseData.suppressResponseFilterExceptions()) {
                try {
                    runResponseFilters(requestContext, responseContext, methodBindings);
                } catch (IOException ex) {
                    // Suppressed for error responses to match previous behavior
                }
            } else {
                try {
                    runResponseFilters(requestContext, responseContext, methodBindings);
                } catch (IOException ex) {
                    throw new RuntimeException("Response filter failed", ex);
                }
            }
        }

        copyResponseHeaders(res, responseContext);

        String output = responseData.output();
        String contentType = responseData.contentType();
        if (output != null) {
            if (contentType != null) {
                res.header("Content-Type", contentType);
            }
            res.status(responseContext.getStatus()).send(output);
        } else {
            res.status(responseContext.getStatus()).send();
        }
    }

    private void runResponseFilters(HelidonContainerRequestContext requestContext,
                                    HelidonContainerResponseContext responseContext,
                                    Set<String> methodBindings) throws IOException {
        for (FilterContext.FilterEntry<ContainerResponseFilter> entry
                : filterContext.getResponseFiltersWithBindings()) {
            if (entry.matches(methodBindings)) {
                entry.filter().filter(requestContext, responseContext);
            }
        }
    }

    private void copyResponseHeaders(ServerResponse res, HelidonContainerResponseContext responseContext) {
        for (var entry : responseContext.getHeaders().entrySet()) {
            for (var value : entry.getValue()) {
                if (value != null) {
                    res.header(io.helidon.http.HeaderNames.create(entry.getKey()), value.toString());
                }
            }
        }
    }

    private void sendAbortResponse(ServerResponse res, HelidonContainerRequestContext requestContext) {
        String msg = requestContext.getAbortMessage();
        res.status(requestContext.getAbortStatus()).send(msg != null ? msg : "");
    }
}
