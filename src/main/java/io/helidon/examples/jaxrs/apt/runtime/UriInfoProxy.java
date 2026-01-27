package io.helidon.examples.jaxrs.apt.runtime;

import io.helidon.common.context.Contexts;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.List;

/**
 * Proxy implementation of UriInfo that delegates to the current request's UriInfo
 * via Helidon's Context mechanism.
 *
 * <p>This proxy is injected into filter's @Context UriInfo fields at construction time.
 * When methods are called, it retrieves the actual UriInfo from the current request's
 * context, enabling thread-safe request-scoped injection with singleton filters.
 */
public class UriInfoProxy implements UriInfo {

    /**
     * Singleton instance to be injected into filters.
     */
    public static final UriInfoProxy INSTANCE = new UriInfoProxy();

    private UriInfoProxy() {
    }

    private UriInfo delegate() {
        return Contexts.context()
                .flatMap(ctx -> ctx.get(UriInfo.class, UriInfo.class))
                .orElseThrow(() -> new IllegalStateException(
                        "UriInfo not available - ensure JaxRsContextFilter is registered"));
    }

    @Override
    public String getPath() {
        return delegate().getPath();
    }

    @Override
    public String getPath(boolean decode) {
        return delegate().getPath(decode);
    }

    @Override
    public List<PathSegment> getPathSegments() {
        return delegate().getPathSegments();
    }

    @Override
    public List<PathSegment> getPathSegments(boolean decode) {
        return delegate().getPathSegments(decode);
    }

    @Override
    public URI getRequestUri() {
        return delegate().getRequestUri();
    }

    @Override
    public UriBuilder getRequestUriBuilder() {
        return delegate().getRequestUriBuilder();
    }

    @Override
    public URI getAbsolutePath() {
        return delegate().getAbsolutePath();
    }

    @Override
    public UriBuilder getAbsolutePathBuilder() {
        return delegate().getAbsolutePathBuilder();
    }

    @Override
    public URI getBaseUri() {
        return delegate().getBaseUri();
    }

    @Override
    public UriBuilder getBaseUriBuilder() {
        return delegate().getBaseUriBuilder();
    }

    @Override
    public MultivaluedMap<String, String> getPathParameters() {
        return delegate().getPathParameters();
    }

    @Override
    public MultivaluedMap<String, String> getPathParameters(boolean decode) {
        return delegate().getPathParameters(decode);
    }

    @Override
    public MultivaluedMap<String, String> getQueryParameters() {
        return delegate().getQueryParameters();
    }

    @Override
    public MultivaluedMap<String, String> getQueryParameters(boolean decode) {
        return delegate().getQueryParameters(decode);
    }

    @Override
    public List<String> getMatchedURIs() {
        return delegate().getMatchedURIs();
    }

    @Override
    public List<String> getMatchedURIs(boolean decode) {
        return delegate().getMatchedURIs(decode);
    }

    @Override
    public List<Object> getMatchedResources() {
        return delegate().getMatchedResources();
    }

    @Override
    public URI resolve(URI uri) {
        return delegate().resolve(uri);
    }

    @Override
    public URI relativize(URI uri) {
        return delegate().relativize(uri);
    }
}
