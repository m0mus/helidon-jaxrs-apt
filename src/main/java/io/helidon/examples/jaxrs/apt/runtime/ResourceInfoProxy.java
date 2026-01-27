package io.helidon.examples.jaxrs.apt.runtime;

import io.helidon.common.context.Contexts;
import jakarta.ws.rs.container.ResourceInfo;

import java.lang.reflect.Method;

/**
 * Proxy implementation of ResourceInfo that delegates to the current request's ResourceInfo
 * via Helidon's Context mechanism.
 *
 * <p>This proxy is injected into filter's @Context ResourceInfo fields at construction time.
 * When methods are called, it retrieves the actual ResourceInfo from the current request's
 * context, enabling thread-safe request-scoped injection with singleton filters.
 */
public class ResourceInfoProxy implements ResourceInfo {

    /**
     * Singleton instance to be injected into filters.
     */
    public static final ResourceInfoProxy INSTANCE = new ResourceInfoProxy();

    private ResourceInfoProxy() {
    }

    private ResourceInfo delegate() {
        return Contexts.context()
                .flatMap(ctx -> ctx.get(ResourceInfo.class, ResourceInfo.class))
                .orElseThrow(() -> new IllegalStateException(
                        "ResourceInfo not available - ensure this is called from a post-matching filter"));
    }

    @Override
    public Method getResourceMethod() {
        return delegate().getResourceMethod();
    }

    @Override
    public Class<?> getResourceClass() {
        return delegate().getResourceClass();
    }
}
