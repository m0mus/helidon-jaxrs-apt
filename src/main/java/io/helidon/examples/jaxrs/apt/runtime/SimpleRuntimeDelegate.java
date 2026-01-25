package io.helidon.examples.jaxrs.apt.runtime;

import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.RuntimeDelegate;

import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal RuntimeDelegate implementation to support JAX-RS exceptions.
 * This is needed because JAX-RS exceptions like NotFoundException internally
 * use Response.status() which requires RuntimeDelegate.
 */
public class SimpleRuntimeDelegate extends RuntimeDelegate {

    static {
        RuntimeDelegate.setInstance(new SimpleRuntimeDelegate());
    }

    /**
     * Initialize the RuntimeDelegate. Call this early in application startup.
     */
    public static void init() {
        // Static initializer does the work
    }

    @Override
    public UriBuilder createUriBuilder() {
        throw new UnsupportedOperationException("UriBuilder not supported");
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
    public <T> HeaderDelegate<T> createHeaderDelegate(Class<T> type) {
        throw new UnsupportedOperationException("HeaderDelegate not supported");
    }

    @Override
    public Link.Builder createLinkBuilder() {
        throw new UnsupportedOperationException("LinkBuilder not supported");
    }

    @Override
    public jakarta.ws.rs.core.EntityPart.Builder createEntityPartBuilder(String partName) {
        throw new UnsupportedOperationException("EntityPartBuilder not supported");
    }

    @Override
    public java.util.concurrent.CompletionStage<jakarta.ws.rs.SeBootstrap.Instance> bootstrap(
            Class<? extends Application> application, jakarta.ws.rs.SeBootstrap.Configuration configuration) {
        throw new UnsupportedOperationException("SeBootstrap not supported");
    }

    @Override
    public java.util.concurrent.CompletionStage<jakarta.ws.rs.SeBootstrap.Instance> bootstrap(
            Application application, jakarta.ws.rs.SeBootstrap.Configuration configuration) {
        throw new UnsupportedOperationException("SeBootstrap not supported");
    }

    @Override
    public jakarta.ws.rs.SeBootstrap.Configuration.Builder createConfigurationBuilder() {
        throw new UnsupportedOperationException("SeBootstrap Configuration not supported");
    }

    /**
     * Simple Response.ResponseBuilder that just tracks status.
     */
    private static class SimpleResponseBuilder extends Response.ResponseBuilder {
        private int status = 200;
        private String reasonPhrase;
        private Object entity;

        @Override
        public Response build() {
            return new SimpleResponse(status, reasonPhrase, entity);
        }

        @Override
        public Response.ResponseBuilder clone() {
            SimpleResponseBuilder clone = new SimpleResponseBuilder();
            clone.status = this.status;
            clone.reasonPhrase = this.reasonPhrase;
            clone.entity = this.entity;
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
            return this;
        }

        @Override
        public Response.ResponseBuilder replaceAll(MultivaluedMap<String, Object> headers) {
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
            return this;
        }

        @Override
        public Response.ResponseBuilder type(String type) {
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

    /**
     * Simple Response implementation.
     */
    private static class SimpleResponse extends Response {
        private final int status;
        private final String reasonPhrase;
        private final Object entity;

        SimpleResponse(int status, String reasonPhrase, Object entity) {
            this.status = status;
            this.reasonPhrase = reasonPhrase;
            this.entity = entity;
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
        public <T> T readEntity(GenericType<T> entityType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T readEntity(Class<T> entityType, java.lang.annotation.Annotation[] annotations) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T readEntity(GenericType<T> entityType, java.lang.annotation.Annotation[] annotations) {
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
            return null;
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
            return new MultivaluedHashMap<>();
        }

        @Override
        public MultivaluedMap<String, String> getStringHeaders() {
            return new MultivaluedHashMap<>();
        }

        @Override
        public String getHeaderString(String name) {
            return null;
        }
    }
}
