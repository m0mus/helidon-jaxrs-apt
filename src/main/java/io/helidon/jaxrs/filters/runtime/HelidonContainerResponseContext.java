package io.helidon.jaxrs.filters.runtime;

import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.*;

import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.*;

/**
 * Simple implementation of ContainerResponseContext for filter processing.
 */
public class HelidonContainerResponseContext implements ContainerResponseContext {

    private int status;
    private Object entity;
    private final MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
    private final MultivaluedMap<String, String> stringHeaders = new MultivaluedHashMap<>();
    private MediaType mediaType;

    public HelidonContainerResponseContext(int status, Object entity) {
        this.status = status;
        this.entity = entity;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void setStatus(int code) {
        this.status = code;
    }

    @Override
    public Response.StatusType getStatusInfo() {
        Response.Status statusEnum = Response.Status.fromStatusCode(status);
        if (statusEnum != null) {
            return statusEnum;
        }
        return new Response.StatusType() {
            @Override
            public int getStatusCode() {
                return status;
            }

            @Override
            public Response.Status.Family getFamily() {
                return Response.Status.Family.familyOf(status);
            }

            @Override
            public String getReasonPhrase() {
                return "";
            }
        };
    }

    @Override
    public void setStatusInfo(Response.StatusType statusInfo) {
        if (statusInfo == null) {
            return;
        }
        this.status = statusInfo.getStatusCode();
    }

    @Override
    public MultivaluedMap<String, Object> getHeaders() {
        return headers;
    }

    @Override
    public MultivaluedMap<String, String> getStringHeaders() {
        return stringHeaders;
    }

    @Override
    public String getHeaderString(String name) {
        List<String> values = stringHeaders.get(name);
        if (values == null || values.isEmpty()) {
            List<Object> rawValues = headers.get(name);
            if (rawValues == null || rawValues.isEmpty()) {
                return null;
            }
            List<String> mapped = new ArrayList<>();
            for (Object value : rawValues) {
                if (value != null) {
                    mapped.add(value.toString());
                }
            }
            return mapped.isEmpty() ? null : String.join(",", mapped);
        }
        return String.join(",", values);
    }

    @Override
    public Set<String> getAllowedMethods() {
        return Set.of();
    }

    @Override
    public Date getDate() {
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
    public MediaType getMediaType() {
        return mediaType;
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
    public Date getLastModified() {
        return null;
    }

    @Override
    public URI getLocation() {
        return null;
    }

    @Override
    public Set<Link> getLinks() {
        return Set.of();
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
    public boolean hasEntity() {
        return entity != null;
    }

    @Override
    public Object getEntity() {
        return entity;
    }

    @Override
    public Class<?> getEntityClass() {
        return entity != null ? entity.getClass() : null;
    }

    @Override
    public Type getEntityType() {
        return getEntityClass();
    }

    @Override
    public void setEntity(Object entity) {
        this.entity = entity;
    }

    @Override
    public void setEntity(Object entity, Annotation[] annotations, MediaType mediaType) {
        this.entity = entity;
        this.mediaType = mediaType;
    }

    @Override
    public Annotation[] getEntityAnnotations() {
        return new Annotation[0];
    }

    @Override
    public OutputStream getEntityStream() {
        throw new UnsupportedOperationException("getEntityStream not supported");
    }

    @Override
    public void setEntityStream(OutputStream outputStream) {
        throw new UnsupportedOperationException("setEntityStream not supported");
    }
}

