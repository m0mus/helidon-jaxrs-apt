package io.helidon.jaxrs.filters.runtime;

import jakarta.ws.rs.SeBootstrap;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.Link;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.Variant;
import jakarta.ws.rs.ext.RuntimeDelegate;

import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Minimal RuntimeDelegate implementation to support filter-only JAX-RS usage.
 */
public class SimpleRuntimeDelegate extends RuntimeDelegate {

    static {
        RuntimeDelegate.setInstance(new SimpleRuntimeDelegate());
    }

    /**
     * Initialize the RuntimeDelegate if not already set.
     */
    public static void init() {
        // Static initializer sets the instance.
    }

    @Override
    public UriBuilder createUriBuilder() {
        return new SimpleUriBuilder();
    }

    @Override
    public Response.ResponseBuilder createResponseBuilder() {
        return new SimpleResponseBuilder();
    }

    @Override
    public Variant.VariantListBuilder createVariantListBuilder() {
        throw new UnsupportedOperationException("VariantListBuilder not supported");
    }

    @Override
    public <T> T createEndpoint(Application application, Class<T> endpointType) {
        throw new UnsupportedOperationException("createEndpoint not supported");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> HeaderDelegate<T> createHeaderDelegate(Class<T> type) {
        if (type == MediaType.class) {
            return (HeaderDelegate<T>) new MediaTypeHeaderDelegate();
        }
        if (type == Date.class) {
            return (HeaderDelegate<T>) new DateHeaderDelegate();
        }
        if (type == CacheControl.class) {
            return (HeaderDelegate<T>) new CacheControlHeaderDelegate();
        }
        if (type == jakarta.ws.rs.core.Cookie.class) {
            return (HeaderDelegate<T>) new CookieHeaderDelegate();
        }
        if (type == NewCookie.class) {
            return (HeaderDelegate<T>) new NewCookieHeaderDelegate();
        }
        return (HeaderDelegate<T>) new ToStringHeaderDelegate();
    }

    @Override
    public Link.Builder createLinkBuilder() {
        throw new UnsupportedOperationException("LinkBuilder not supported");
    }

    @Override
    public SeBootstrap.Configuration.Builder createConfigurationBuilder() {
        throw new UnsupportedOperationException("SeBootstrap Configuration not supported");
    }

    @Override
    public CompletionStage<SeBootstrap.Instance> bootstrap(Application application, SeBootstrap.Configuration configuration) {
        throw new UnsupportedOperationException("SeBootstrap not supported");
    }

    @Override
    public CompletionStage<SeBootstrap.Instance> bootstrap(Class<? extends Application> application,
                                                           SeBootstrap.Configuration configuration) {
        throw new UnsupportedOperationException("SeBootstrap not supported");
    }

    @Override
    public jakarta.ws.rs.core.EntityPart.Builder createEntityPartBuilder(String partName) {
        throw new UnsupportedOperationException("EntityPartBuilder not supported");
    }

    private static class MediaTypeHeaderDelegate implements HeaderDelegate<MediaType> {
        @Override
        public MediaType fromString(String value) {
            if (value == null) {
                return null;
            }
            String[] parts = value.split(";");
            String typePart = parts[0].trim();
            String[] typeParts = typePart.split("/", 2);
            String type = typeParts.length > 0 ? typeParts[0].trim() : "*";
            String subtype = typeParts.length > 1 ? typeParts[1].trim() : "*";
            Map<String, String> params = new java.util.LinkedHashMap<>();
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.isEmpty()) {
                    continue;
                }
                String[] kv = part.split("=", 2);
                String key = kv[0].trim();
                String val = kv.length > 1 ? kv[1].trim() : "";
                if (!key.isEmpty()) {
                    params.put(key, val);
                }
            }
            return new MediaType(type, subtype, params);
        }

        @Override
        public String toString(MediaType value) {
            if (value == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(value.getType()).append("/").append(value.getSubtype());
            for (Map.Entry<String, String> entry : value.getParameters().entrySet()) {
                sb.append(";").append(entry.getKey()).append("=").append(entry.getValue());
            }
            return sb.toString();
        }
    }

    private static class DateHeaderDelegate implements HeaderDelegate<Date> {
        @Override
        public Date fromString(String value) {
            return null;
        }

        @Override
        public String toString(Date value) {
            return value != null ? value.toString() : null;
        }
    }

    private static class CacheControlHeaderDelegate implements HeaderDelegate<CacheControl> {
        @Override
        public CacheControl fromString(String value) {
            return null;
        }

        @Override
        public String toString(CacheControl value) {
            return value != null ? value.toString() : null;
        }
    }

    private static class CookieHeaderDelegate implements HeaderDelegate<jakarta.ws.rs.core.Cookie> {
        @Override
        public jakarta.ws.rs.core.Cookie fromString(String value) {
            if (value == null || value.isEmpty()) {
                return null;
            }
            String[] parts = value.split("=", 2);
            if (parts.length == 2) {
                return new jakarta.ws.rs.core.Cookie(parts[0].trim(), parts[1].trim());
            }
            return new jakarta.ws.rs.core.Cookie(value.trim(), "");
        }

        @Override
        public String toString(jakarta.ws.rs.core.Cookie value) {
            return value != null ? value.toString() : null;
        }
    }

    private static class NewCookieHeaderDelegate implements HeaderDelegate<NewCookie> {
        @Override
        public NewCookie fromString(String value) {
            return null;
        }

        @Override
        public String toString(NewCookie value) {
            return value != null ? value.toString() : null;
        }
    }

    private static class ToStringHeaderDelegate implements HeaderDelegate<Object> {
        @Override
        public Object fromString(String value) {
            return value;
        }

        @Override
        public String toString(Object value) {
            return value != null ? value.toString() : null;
        }
    }

    private static class SimpleResponseBuilder extends Response.ResponseBuilder {
        private int status = 200;
        private String reasonPhrase;
        private Object entity;
        private MediaType mediaType;
        private final MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();

        @Override
        public Response build() {
            return new SimpleResponse(status, reasonPhrase, entity, mediaType, headers);
        }

        @Override
        public Response.ResponseBuilder clone() {
            SimpleResponseBuilder clone = new SimpleResponseBuilder();
            clone.status = this.status;
            clone.reasonPhrase = this.reasonPhrase;
            clone.entity = this.entity;
            clone.mediaType = this.mediaType;
            clone.headers.putAll(this.headers);
            return clone;
        }

        @Override
        public Response.ResponseBuilder status(int status) {
            this.status = status;
            return this;
        }

        @Override
        public Response.ResponseBuilder status(int status, String reasonPhrase) {
            this.status = status;
            this.reasonPhrase = reasonPhrase;
            return this;
        }

        @Override
        public Response.ResponseBuilder entity(Object entity) {
            this.entity = entity;
            return this;
        }

        @Override
        public Response.ResponseBuilder entity(Object entity, java.lang.annotation.Annotation[] annotations) {
            this.entity = entity;
            return this;
        }

        @Override
        public Response.ResponseBuilder allow(String... methods) {
            return this;
        }

        @Override
        public Response.ResponseBuilder allow(java.util.Set<String> methods) {
            return this;
        }

        @Override
        public Response.ResponseBuilder cacheControl(CacheControl cacheControl) {
            return this;
        }

        @Override
        public Response.ResponseBuilder encoding(String encoding) {
            return this;
        }

        @Override
        public Response.ResponseBuilder header(String name, Object value) {
            if (value != null) {
                headers.add(name, value);
            }
            return this;
        }

        @Override
        public Response.ResponseBuilder replaceAll(MultivaluedMap<String, Object> headers) {
            this.headers.clear();
            if (headers != null) {
                this.headers.putAll(headers);
            }
            return this;
        }

        @Override
        public Response.ResponseBuilder language(String language) {
            return this;
        }

        @Override
        public Response.ResponseBuilder language(Locale language) {
            return this;
        }

        @Override
        public Response.ResponseBuilder type(MediaType type) {
            this.mediaType = type;
            return this;
        }

        @Override
        public Response.ResponseBuilder type(String type) {
            this.mediaType = type != null ? MediaType.valueOf(type) : null;
            return this;
        }

        @Override
        public Response.ResponseBuilder variant(Variant variant) {
            return this;
        }

        @Override
        public Response.ResponseBuilder contentLocation(URI location) {
            return this;
        }

        @Override
        public Response.ResponseBuilder cookie(NewCookie... cookies) {
            return this;
        }

        @Override
        public Response.ResponseBuilder expires(Date expires) {
            return this;
        }

        @Override
        public Response.ResponseBuilder lastModified(Date lastModified) {
            return this;
        }

        @Override
        public Response.ResponseBuilder location(URI location) {
            return this;
        }

        @Override
        public Response.ResponseBuilder tag(EntityTag tag) {
            return this;
        }

        @Override
        public Response.ResponseBuilder tag(String tag) {
            return this;
        }

        @Override
        public Response.ResponseBuilder variants(Variant... variants) {
            return this;
        }

        @Override
        public Response.ResponseBuilder variants(List<Variant> variants) {
            return this;
        }

        @Override
        public Response.ResponseBuilder links(Link... links) {
            return this;
        }

        @Override
        public Response.ResponseBuilder link(URI uri, String rel) {
            return this;
        }

        @Override
        public Response.ResponseBuilder link(String uri, String rel) {
            return this;
        }
    }

    private static class SimpleResponse extends Response {
        private final int status;
        private final String reasonPhrase;
        private final Object entity;
        private final MediaType mediaType;
        private final MultivaluedMap<String, Object> headers;

        SimpleResponse(int status, String reasonPhrase, Object entity, MediaType mediaType,
                       MultivaluedMap<String, Object> headers) {
            this.status = status;
            this.reasonPhrase = reasonPhrase;
            this.entity = entity;
            this.mediaType = mediaType;
            this.headers = headers != null ? headers : new MultivaluedHashMap<>();
        }

        @Override
        public int getStatus() {
            return status;
        }

        @Override
        public StatusType getStatusInfo() {
            return new StatusType() {
                @Override
                public int getStatusCode() {
                    return status;
                }

                @Override
                public Status.Family getFamily() {
                    return Status.Family.familyOf(status);
                }

                @Override
                public String getReasonPhrase() {
                    return reasonPhrase != null ? reasonPhrase : "";
                }
            };
        }

        @Override
        public Object getEntity() {
            return entity;
        }

        @Override
        public <T> T readEntity(Class<T> entityType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T readEntity(jakarta.ws.rs.core.GenericType<T> entityType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T readEntity(Class<T> entityType, java.lang.annotation.Annotation[] annotations) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T readEntity(jakarta.ws.rs.core.GenericType<T> entityType, java.lang.annotation.Annotation[] annotations) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasEntity() {
            return entity != null;
        }

        @Override
        public boolean bufferEntity() {
            return false;
        }

        @Override
        public void close() {
        }

        @Override
        public MediaType getMediaType() {
            return mediaType;
        }

        @Override
        public Locale getLanguage() {
            return null;
        }

        @Override
        public int getLength() {
            return -1;
        }

        @Override
        public java.util.Set<String> getAllowedMethods() {
            return java.util.Set.of();
        }

        @Override
        public Map<String, NewCookie> getCookies() {
            return Map.of();
        }

        @Override
        public EntityTag getEntityTag() {
            return null;
        }

        @Override
        public Date getDate() {
            return null;
        }

        @Override
        public Date getLastModified() {
            return null;
        }

        @Override
        public URI getLocation() {
            return null;
        }

        @Override
        public java.util.Set<Link> getLinks() {
            return java.util.Set.of();
        }

        @Override
        public boolean hasLink(String relation) {
            return false;
        }

        @Override
        public Link getLink(String relation) {
            return null;
        }

        @Override
        public Link.Builder getLinkBuilder(String relation) {
            return null;
        }

        @Override
        public MultivaluedMap<String, Object> getMetadata() {
            return headers;
        }

        @Override
        public MultivaluedMap<String, String> getStringHeaders() {
            MultivaluedMap<String, String> result = new MultivaluedHashMap<>();
            for (var entry : headers.entrySet()) {
                for (var value : entry.getValue()) {
                    result.add(entry.getKey(), value != null ? value.toString() : null);
                }
            }
            return result;
        }

        @Override
        public String getHeaderString(String name) {
            List<Object> values = headers.get(name);
            if (values == null || values.isEmpty()) {
                return null;
            }
            return values.stream()
                    .map(v -> v != null ? v.toString() : "")
                    .reduce((a, b) -> a + "," + b)
                    .orElse(null);
        }
    }

    private static class SimpleUriBuilder extends UriBuilder {
        private String scheme;
        private String userInfo;
        private String host;
        private int port = -1;
        private String path;
        private String query;
        private String fragment;
        private String schemeSpecificPart;

        @Override
        public UriBuilder clone() {
            SimpleUriBuilder copy = new SimpleUriBuilder();
            copy.scheme = scheme;
            copy.userInfo = userInfo;
            copy.host = host;
            copy.port = port;
            copy.path = path;
            copy.query = query;
            copy.fragment = fragment;
            copy.schemeSpecificPart = schemeSpecificPart;
            return copy;
        }

        @Override
        public UriBuilder uri(URI uri) {
            if (uri != null) {
                scheme = uri.getScheme();
                schemeSpecificPart = uri.getSchemeSpecificPart();
                userInfo = uri.getUserInfo();
                host = uri.getHost();
                port = uri.getPort();
                path = uri.getPath();
                query = uri.getQuery();
                fragment = uri.getFragment();
            }
            return this;
        }

        @Override
        public UriBuilder uri(String uriTemplate) {
            if (uriTemplate != null) {
                uri(URI.create(uriTemplate));
            }
            return this;
        }

        @Override
        public UriBuilder scheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        @Override
        public UriBuilder schemeSpecificPart(String ssp) {
            this.schemeSpecificPart = ssp;
            return this;
        }

        @Override
        public UriBuilder userInfo(String ui) {
            this.userInfo = ui;
            return this;
        }

        @Override
        public UriBuilder host(String host) {
            this.host = host;
            return this;
        }

        @Override
        public UriBuilder port(int port) {
            this.port = port;
            return this;
        }

        @Override
        public UriBuilder replacePath(String path) {
            this.path = path;
            return this;
        }

        @Override
        public UriBuilder path(String path) {
            if (path == null || path.isEmpty()) {
                return this;
            }
            if (this.path == null || this.path.isEmpty()) {
                this.path = path;
            } else if (this.path.endsWith("/") || path.startsWith("/")) {
                this.path = this.path + path;
            } else {
                this.path = this.path + "/" + path;
            }
            return this;
        }

        @Override
        public UriBuilder path(Class resource) {
            return this;
        }

        @Override
        public UriBuilder path(Class resource, String method) {
            return this;
        }

        @Override
        public UriBuilder path(java.lang.reflect.Method method) {
            return this;
        }

        @Override
        public UriBuilder segment(String... segments) {
            if (segments != null) {
                for (String segment : segments) {
                    path(segment);
                }
            }
            return this;
        }

        @Override
        public UriBuilder replaceMatrix(String matrix) {
            return this;
        }

        @Override
        public UriBuilder matrixParam(String name, Object... values) {
            return this;
        }

        @Override
        public UriBuilder replaceMatrixParam(String name, Object... values) {
            return this;
        }

        @Override
        public UriBuilder replaceQuery(String query) {
            this.query = query;
            return this;
        }

        @Override
        public UriBuilder queryParam(String name, Object... values) {
            if (name == null || values == null || values.length == 0) {
                return this;
            }
            StringBuilder sb = new StringBuilder(query != null ? query : "");
            for (Object value : values) {
                if (!sb.isEmpty()) {
                    sb.append("&");
                }
                sb.append(name).append("=").append(value);
            }
            query = sb.toString();
            return this;
        }

        @Override
        public UriBuilder replaceQueryParam(String name, Object... values) {
            return queryParam(name, values);
        }

        @Override
        public UriBuilder fragment(String fragment) {
            this.fragment = fragment;
            return this;
        }

        @Override
        public UriBuilder resolveTemplate(String name, Object value) {
            return this;
        }

        @Override
        public UriBuilder resolveTemplate(String name, Object value, boolean encodeSlashInPath) {
            return this;
        }

        @Override
        public UriBuilder resolveTemplateFromEncoded(String name, Object value) {
            return this;
        }

        @Override
        public UriBuilder resolveTemplates(Map<String, Object> templateValues) {
            return this;
        }

        @Override
        public UriBuilder resolveTemplates(Map<String, Object> templateValues, boolean encodeSlashInPath) {
            return this;
        }

        @Override
        public UriBuilder resolveTemplatesFromEncoded(Map<String, Object> templateValues) {
            return this;
        }

        @Override
        public URI buildFromMap(Map<String, ?> values) {
            return build();
        }

        @Override
        public URI buildFromMap(Map<String, ?> values, boolean encodeSlashInPath) {
            return build();
        }

        @Override
        public URI buildFromEncodedMap(Map<String, ?> values) {
            return build();
        }

        @Override
        public URI build(Object... values) {
            return buildInternal();
        }

        @Override
        public URI build(Object[] values, boolean encodeSlashInPath) {
            return buildInternal();
        }

        @Override
        public URI buildFromEncoded(Object... values) {
            return buildInternal();
        }

        @Override
        public String toTemplate() {
            return buildInternal().toString();
        }

        private URI buildInternal() {
            if (schemeSpecificPart != null && scheme != null && host == null) {
                return URI.create(scheme + ":" + schemeSpecificPart);
            }
            return URI.create(String.format("%s://%s%s%s%s",
                    scheme != null ? scheme : "http",
                    host != null ? host : "localhost",
                    port >= 0 ? ":" + port : "",
                    path != null ? path : "",
                    query != null ? "?" + query : ""));
        }
    }
}

