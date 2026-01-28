package io.helidon.jaxrs.filters.runtime;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;

/**
 * Helidon Filter that sets up context propagation for JAX-RS request processing.
 *
 * <p>This filter ensures that {@link Contexts#context()} returns the request's context
 * during the entire request processing chain. This enables:
 * <ul>
 *   <li>Request-scoped @Context injection in filters via proxies</li>
 *   <li>Access to UriInfo, HttpHeaders, SecurityContext from anywhere in the call chain</li>
 * </ul>
 *
 * <p>This filter registers {@link UriInfo}, {@link HttpHeaders}, and {@link SecurityContext}
 * in the request context before proceeding. This makes these objects available to both
 * pre-matching and post-matching filters via their respective proxy classes.
 *
 * <p>Note: {@link jakarta.ws.rs.container.ResourceInfo} is NOT registered here because
 * it depends on route matching, which happens later in the chain.
 *
 * <p>This filter should be registered before any other filters to ensure context is
 * available throughout request processing.
 */
public class JaxRsContextFilter implements Filter {

    /**
     * Singleton instance.
     */
    public static final JaxRsContextFilter INSTANCE = new JaxRsContextFilter();

    private JaxRsContextFilter() {
    }

    @Override
    public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
        Context ctx = req.context();

        // Register request-scoped JAX-RS context objects BEFORE proceeding
        // This makes them available to pre-matching filters
        ctx.register(UriInfo.class, new HelidonUriInfo(req));
        ctx.register(HttpHeaders.class, new HelidonHttpHeaders(req));
        ctx.register(SecurityContext.class, new HelidonSecurityContext(req));

        // Wrap request processing in the request's context
        // This makes Contexts.context() return req.context() for all downstream processing
        Contexts.runInContext(ctx, chain::proceed);
    }
}

