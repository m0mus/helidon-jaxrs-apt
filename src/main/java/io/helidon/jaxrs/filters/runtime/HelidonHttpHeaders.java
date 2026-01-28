package io.helidon.jaxrs.filters.runtime;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.webserver.http.ServerRequest;
import jakarta.ws.rs.core.*;

import java.util.*;

/**
 * Wrapper that adapts Helidon's ServerRequest headers to JAX-RS HttpHeaders.
 */
public class HelidonHttpHeaders implements HttpHeaders {

    private final ServerRequest request;

    public HelidonHttpHeaders(ServerRequest request) {
        this.request = request;
    }

    @Override
    public List<String> getRequestHeader(String name) {
        HeaderName headerName = HeaderNames.create(name);
        return request.headers().all(headerName, List::of);
    }

    @Override
    public String getHeaderString(String name) {
        HeaderName headerName = HeaderNames.create(name);
        return request.headers().first(headerName).map(Object::toString).orElse(null);
    }

    @Override
    public MultivaluedMap<String, String> getRequestHeaders() {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        for (var header : request.headers()) {
            headers.add(header.name(), header.values());
        }
        return headers;
    }

    @Override
    public List<MediaType> getAcceptableMediaTypes() {
        String accept = getHeaderString("Accept");
        if (accept == null || accept.isEmpty()) {
            return List.of(MediaType.WILDCARD_TYPE);
        }
        List<MediaType> types = new ArrayList<>();
        for (String part : accept.split(",")) {
            try {
                types.add(MediaType.valueOf(part.trim()));
            } catch (IllegalArgumentException e) {
                // Skip invalid media types
            }
        }
        return types.isEmpty() ? List.of(MediaType.WILDCARD_TYPE) : types;
    }

    @Override
    public List<Locale> getAcceptableLanguages() {
        String acceptLang = getHeaderString("Accept-Language");
        if (acceptLang == null || acceptLang.isEmpty()) {
            return List.of(Locale.getDefault());
        }
        List<Locale> locales = new ArrayList<>();
        for (String part : acceptLang.split(",")) {
            String lang = part.split(";")[0].trim();
            if (!lang.isEmpty()) {
                locales.add(Locale.forLanguageTag(lang));
            }
        }
        return locales.isEmpty() ? List.of(Locale.getDefault()) : locales;
    }

    @Override
    public MediaType getMediaType() {
        String contentType = getHeaderString("Content-Type");
        return contentType != null ? MediaType.valueOf(contentType) : null;
    }

    @Override
    public Locale getLanguage() {
        String lang = getHeaderString("Content-Language");
        return lang != null ? Locale.forLanguageTag(lang) : null;
    }

    @Override
    public Map<String, Cookie> getCookies() {
        Map<String, Cookie> cookies = new HashMap<>();
        for (var entry : request.headers().cookies().toMap().entrySet()) {
            String name = entry.getKey();
            List<String> values = entry.getValue();
            if (!values.isEmpty()) {
                cookies.put(name, new Cookie(name, values.get(0)));
            }
        }
        return cookies;
    }

    @Override
    public Date getDate() {
        String date = getHeaderString("Date");
        // Simplified: return null for now
        return null;
    }

    @Override
    public int getLength() {
        String length = getHeaderString("Content-Length");
        if (length != null) {
            try {
                return Integer.parseInt(length);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }
}

