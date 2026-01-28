package io.helidon.jaxrs.filters;

import io.helidon.common.context.Contexts;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Proxy implementation of HttpHeaders that delegates to the current request's HttpHeaders
 * via Helidon's Context mechanism.
 *
 * <p>This proxy is injected into filter's @Context HttpHeaders fields at construction time.
 * When methods are called, it retrieves the actual HttpHeaders from the current request's
 * context, enabling thread-safe request-scoped injection with singleton filters.
 */
public class HttpHeadersProxy implements HttpHeaders {

    /**
     * Singleton instance to be injected into filters.
     */
    public static final HttpHeadersProxy INSTANCE = new HttpHeadersProxy();

    private HttpHeadersProxy() {
    }

    private HttpHeaders delegate() {
        return Contexts.context()
                .flatMap(ctx -> ctx.get(HttpHeaders.class, HttpHeaders.class))
                .orElseThrow(() -> new IllegalStateException(
                        "HttpHeaders not available - ensure JaxRsContextFilter is registered"));
    }

    @Override
    public List<String> getRequestHeader(String name) {
        return delegate().getRequestHeader(name);
    }

    @Override
    public String getHeaderString(String name) {
        return delegate().getHeaderString(name);
    }

    @Override
    public MultivaluedMap<String, String> getRequestHeaders() {
        return delegate().getRequestHeaders();
    }

    @Override
    public List<MediaType> getAcceptableMediaTypes() {
        return delegate().getAcceptableMediaTypes();
    }

    @Override
    public List<Locale> getAcceptableLanguages() {
        return delegate().getAcceptableLanguages();
    }

    @Override
    public MediaType getMediaType() {
        return delegate().getMediaType();
    }

    @Override
    public Locale getLanguage() {
        return delegate().getLanguage();
    }

    @Override
    public Map<String, Cookie> getCookies() {
        return delegate().getCookies();
    }

    @Override
    public Date getDate() {
        return delegate().getDate();
    }

    @Override
    public int getLength() {
        return delegate().getLength();
    }
}


