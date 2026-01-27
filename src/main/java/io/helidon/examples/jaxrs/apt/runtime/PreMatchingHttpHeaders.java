package io.helidon.examples.jaxrs.apt.runtime;

import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HttpHeaders implementation backed by PreMatchingRequestContext.
 */
public class PreMatchingHttpHeaders implements HttpHeaders {

    private final PreMatchingRequestContext context;

    public PreMatchingHttpHeaders(PreMatchingRequestContext context) {
        this.context = context;
    }

    @Override
    public List<String> getRequestHeader(String name) {
        return context.getHeaders().getOrDefault(name, List.of());
    }

    @Override
    public String getHeaderString(String name) {
        return context.getHeaderString(name);
    }

    @Override
    public MultivaluedMap<String, String> getRequestHeaders() {
        return context.getHeaders();
    }

    @Override
    public List<MediaType> getAcceptableMediaTypes() {
        return context.getAcceptableMediaTypes();
    }

    @Override
    public List<Locale> getAcceptableLanguages() {
        return context.getAcceptableLanguages();
    }

    @Override
    public MediaType getMediaType() {
        return context.getMediaType();
    }

    @Override
    public Locale getLanguage() {
        return context.getLanguage();
    }

    @Override
    public Map<String, Cookie> getCookies() {
        return context.getCookies();
    }

    @Override
    public Date getDate() {
        return context.getDate();
    }

    @Override
    public int getLength() {
        return context.getLength();
    }
}
