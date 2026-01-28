package io.helidon.jaxrs.filters;

import io.helidon.common.context.Contexts;
import jakarta.ws.rs.core.SecurityContext;

import java.security.Principal;

/**
 * Proxy implementation of SecurityContext that delegates to the current request's SecurityContext
 * via Helidon's Context mechanism.
 *
 * <p>This proxy is injected into filter's @Context SecurityContext fields at construction time.
 * When methods are called, it retrieves the actual SecurityContext from the current request's
 * context, enabling thread-safe request-scoped injection with singleton filters.
 */
public class SecurityContextProxy implements SecurityContext {

    /**
     * Singleton instance to be injected into filters.
     */
    public static final SecurityContextProxy INSTANCE = new SecurityContextProxy();

    private SecurityContextProxy() {
    }

    private SecurityContext delegate() {
        return Contexts.context()
                .flatMap(ctx -> ctx.get(SecurityContext.class, SecurityContext.class))
                .orElseThrow(() -> new IllegalStateException(
                        "SecurityContext not available - ensure JaxRsContextFilter is registered"));
    }

    @Override
    public Principal getUserPrincipal() {
        return delegate().getUserPrincipal();
    }

    @Override
    public boolean isUserInRole(String role) {
        return delegate().isUserInRole(role);
    }

    @Override
    public boolean isSecure() {
        return delegate().isSecure();
    }

    @Override
    public String getAuthenticationScheme() {
        return delegate().getAuthenticationScheme();
    }
}


