package io.helidon.jaxrs.filters.runtime;

import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.webserver.http.RoutingRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;

import java.io.InputStream;
import java.net.URI;
import java.util.*;

/**
 * ContainerRequestContext implementation for pre-matching filters.
 *
 * <p>Unlike the post-matching context, this implementation supports:
 * <ul>
 *   <li>{@link #setRequestUri(URI)} - to modify the request URI before routing</li>
 *   <li>{@link #setMethod(String)} - to modify the HTTP method before routing</li>
 *   <li>Header modifications via {@link #getHeaders()}</li>
 * </ul>
 *
 * <p>Modifications are tracked and can be applied to the underlying Helidon request
 * via {@link #buildModifiedPrologue()}.
 */
public class PreMatchingRequestContext implements ContainerRequestContext {

    private final RoutingRequest request;
    private final HttpPrologue originalPrologue;
    private final Map<String, Object> properties = new HashMap<>();
    private final MultivaluedMap<String, String> modifiableHeaders;

    // Mutable state for URI/method modifications
    private URI newBaseUri;
    private URI newRequestUri;
    private String newMethod;

    // Abort state
    private boolean aborted = false;
    private Response abortResponse;

    // Security context
    private SecurityContext securityContext;

    public PreMatchingRequestContext(RoutingRequest request) {
        this.request = request;
        this.originalPrologue = request.prologue();

        // Create modifiable copy of headers
        this.modifiableHeaders = new MultivaluedHashMap<>();
        for (var header : request.headers()) {
            modifiableHeaders.put(header.name(), new ArrayList<>(header.allValues()));
        }
    }

    // ========== URI Modification (key for pre-matching) ==========

    @Override
    public void setRequestUri(URI requestUri) {
        this.newRequestUri = requestUri;
    }

    @Override
    public void setRequestUri(URI baseUri, URI requestUri) {
        this.newBaseUri = baseUri;
        this.newRequestUri = requestUri;
    }

    @Override
    public void setMethod(String method) {
        this.newMethod = method;
    }

    /**
     * Check if the request has been modified (URI or method changed).
     *
     * @return true if modifications need to be applied
     */
    public boolean isModified() {
        return newRequestUri != null || newMethod != null;
    }

    /**
     * Build a new HttpPrologue with the modifications applied.
     *
     * @return modified prologue to apply to the request
     */
    public HttpPrologue buildModifiedPrologue() {
        Method method = newMethod != null
                ? Method.create(newMethod)
                : originalPrologue.method();

        UriPath path;
        UriQuery query;
        UriFragment fragment;

        if (newRequestUri != null) {
            String rawPath = newRequestUri.getRawPath();
            path = rawPath != null ? UriPath.create(rawPath) : originalPrologue.uriPath();

            String rawQuery = newRequestUri.getRawQuery();
            query = rawQuery != null ? UriQuery.create(rawQuery) : originalPrologue.query();

            String rawFragment = newRequestUri.getRawFragment();
            fragment = rawFragment != null ? UriFragment.create(rawFragment) : originalPrologue.fragment();
        } else {
            path = originalPrologue.uriPath();
            query = originalPrologue.query();
            fragment = originalPrologue.fragment();
        }

        return HttpPrologue.create(
                originalPrologue.rawProtocol(),
                originalPrologue.protocol(),
                originalPrologue.protocolVersion(),
                method,
                path,
                query,
                fragment
        );
    }

    // ========== Abort handling ==========

    @Override
    public void abortWith(Response response) {
        this.aborted = true;
        this.abortResponse = response;
    }

    public boolean isAborted() {
        return aborted;
    }

    public Response getAbortResponse() {
        return abortResponse;
    }

    // ========== Properties ==========

    @Override
    public Object getProperty(String name) {
        return properties.get(name);
    }

    @Override
    public Collection<String> getPropertyNames() {
        return properties.keySet();
    }

    @Override
    public void setProperty(String name, Object object) {
        properties.put(name, object);
    }

    @Override
    public void removeProperty(String name) {
        properties.remove(name);
    }

    // ========== URI Info ==========

    @Override
    public UriInfo getUriInfo() {
        // Return UriInfo based on current state (modified or original)
        if (newRequestUri != null) {
            return new ModifiedUriInfo(newBaseUri, newRequestUri, request);
        }
        return new HelidonUriInfo(request);
    }

    // ========== Request Info ==========

    @Override
    public Request getRequest() {
        // JAX-RS Request object - not commonly used in filters
        throw new UnsupportedOperationException("getRequest() not supported in pre-matching filters");
    }

    @Override
    public String getMethod() {
        return newMethod != null ? newMethod : originalPrologue.method().text();
    }

    // ========== Headers (modifiable) ==========

    @Override
    public MultivaluedMap<String, String> getHeaders() {
        return modifiableHeaders;
    }

    @Override
    public String getHeaderString(String name) {
        List<String> values = modifiableHeaders.get(name);
        if ((values == null || values.isEmpty()) && name != null) {
            for (Map.Entry<String, List<String>> entry : modifiableHeaders.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    values = entry.getValue();
                    break;
                }
            }
        }
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(",", values);
    }

    @Override
    public Date getDate() {
        String date = getHeaderString("Date");
        // Simplified implementation
        return null;
    }

    @Override
    public Locale getLanguage() {
        String lang = getHeaderString("Content-Language");
        return lang != null ? Locale.forLanguageTag(lang) : null;
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

    @Override
    public MediaType getMediaType() {
        String contentType = getHeaderString("Content-Type");
        return contentType != null ? MediaType.valueOf(contentType) : null;
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
    public Map<String, Cookie> getCookies() {
        Map<String, Cookie> cookies = new HashMap<>();
        String cookieHeader = getHeaderString("Cookie");
        if (cookieHeader != null) {
            // Simple cookie parsing
            for (String part : cookieHeader.split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2) {
                    cookies.put(kv[0].trim(), new Cookie(kv[0].trim(), kv[1].trim()));
                }
            }
        }
        return cookies;
    }

    // ========== Entity ==========

    @Override
    public boolean hasEntity() {
        return getLength() > 0;
    }

    @Override
    public InputStream getEntityStream() {
        return request.content().inputStream();
    }

    @Override
    public void setEntityStream(InputStream input) {
        throw new UnsupportedOperationException("setEntityStream not supported in pre-matching filters");
    }

    // ========== Security Context ==========

    @Override
    public SecurityContext getSecurityContext() {
        if (securityContext == null) {
            securityContext = new HelidonSecurityContext(request);
        }
        return securityContext;
    }

    @Override
    public void setSecurityContext(SecurityContext context) {
        this.securityContext = context;
    }

    /**
     * Get the modified headers map for applying to subsequent processing.
     *
     * @return the modifiable headers map
     */
    public MultivaluedMap<String, String> getModifiedHeaders() {
        return modifiableHeaders;
    }

    /**
     * Simple UriInfo implementation for modified URIs.
     */
    private static class ModifiedUriInfo implements UriInfo {
        private final URI baseUri;
        private final URI requestUri;
        private final RoutingRequest originalRequest;

        ModifiedUriInfo(URI baseUri, URI requestUri, RoutingRequest originalRequest) {
            this.baseUri = baseUri != null ? baseUri : URI.create("/");
            this.requestUri = requestUri;
            this.originalRequest = originalRequest;
        }

        @Override
        public String getPath() {
            return requestUri.getPath();
        }

        @Override
        public String getPath(boolean decode) {
            return decode ? requestUri.getPath() : requestUri.getRawPath();
        }

        @Override
        public List<PathSegment> getPathSegments() {
            return getPathSegments(true);
        }

        @Override
        public List<PathSegment> getPathSegments(boolean decode) {
            String path = decode ? requestUri.getPath() : requestUri.getRawPath();
            if (path == null || path.isEmpty() || path.equals("/")) {
                return List.of();
            }
            List<PathSegment> segments = new ArrayList<>();
            for (String part : path.split("/")) {
                if (!part.isEmpty()) {
                    segments.add(new SimplePathSegment(part));
                }
            }
            return segments;
        }

        @Override
        public URI getRequestUri() {
            return requestUri;
        }

        @Override
        public UriBuilder getRequestUriBuilder() {
            return UriBuilder.fromUri(requestUri);
        }

        @Override
        public URI getAbsolutePath() {
            return requestUri;
        }

        @Override
        public UriBuilder getAbsolutePathBuilder() {
            return UriBuilder.fromUri(requestUri);
        }

        @Override
        public URI getBaseUri() {
            return baseUri;
        }

        @Override
        public UriBuilder getBaseUriBuilder() {
            return UriBuilder.fromUri(baseUri);
        }

        @Override
        public MultivaluedMap<String, String> getPathParameters() {
            return new MultivaluedHashMap<>(); // No path params before routing
        }

        @Override
        public MultivaluedMap<String, String> getPathParameters(boolean decode) {
            return new MultivaluedHashMap<>();
        }

        @Override
        public MultivaluedMap<String, String> getQueryParameters() {
            return getQueryParameters(true);
        }

        @Override
        public MultivaluedMap<String, String> getQueryParameters(boolean decode) {
            MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
            String query = decode ? requestUri.getQuery() : requestUri.getRawQuery();
            if (query != null) {
                for (String part : query.split("&")) {
                    String[] kv = part.split("=", 2);
                    if (kv.length == 2) {
                        params.add(kv[0], kv[1]);
                    } else if (kv.length == 1) {
                        params.add(kv[0], "");
                    }
                }
            }
            return params;
        }

        @Override
        public List<String> getMatchedURIs() {
            return List.of();
        }

        @Override
        public List<String> getMatchedURIs(boolean decode) {
            return List.of();
        }

        @Override
        public List<Object> getMatchedResources() {
            return List.of();
        }

        @Override
        public URI resolve(URI uri) {
            return baseUri.resolve(uri);
        }

        @Override
        public URI relativize(URI uri) {
            return baseUri.relativize(uri);
        }
    }

    /**
     * Simple PathSegment implementation.
     */
    private static class SimplePathSegment implements PathSegment {
        private final String path;

        SimplePathSegment(String path) {
            this.path = path;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public MultivaluedMap<String, String> getMatrixParameters() {
            return new MultivaluedHashMap<>();
        }
    }
}

