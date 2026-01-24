package io.helidon.examples.jaxrs.apt.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.WriterInterceptorContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of WriterInterceptorContext for intercepting response body writing.
 */
public class HelidonWriterInterceptorContext implements WriterInterceptorContext {

    private final ObjectMapper objectMapper;
    private final Map<String, Object> properties = new HashMap<>();
    private final MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
    private Object entity;
    private OutputStream outputStream;
    private String result;
    private boolean proceeded = false;
    private MediaType mediaType = MediaType.APPLICATION_JSON_TYPE;

    public HelidonWriterInterceptorContext(Object entity, ObjectMapper objectMapper) {
        this.entity = entity;
        this.objectMapper = objectMapper;
        this.outputStream = new ByteArrayOutputStream();
    }

    @Override
    public void proceed() throws IOException {
        if (!proceeded) {
            proceeded = true;
            if (entity != null) {
                result = objectMapper.writeValueAsString(entity);
            } else {
                result = "";
            }
        }
    }

    /**
     * Get the serialized result. Call proceed() first or use this method which calls proceed() automatically.
     */
    public String getResult() throws IOException {
        if (!proceeded) {
            proceed();
        }
        return result;
    }

    @Override
    public Object getEntity() {
        return entity;
    }

    @Override
    public void setEntity(Object entity) {
        this.entity = entity;
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public void setOutputStream(OutputStream os) {
        this.outputStream = os;
    }

    @Override
    public MultivaluedMap<String, Object> getHeaders() {
        return headers;
    }

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

    @Override
    public Annotation[] getAnnotations() {
        return new Annotation[0];
    }

    @Override
    public void setAnnotations(Annotation[] annotations) {
        // Not supported
    }

    @Override
    public Class<?> getType() {
        return entity != null ? entity.getClass() : Object.class;
    }

    @Override
    public void setType(Class<?> type) {
        // Not supported
    }

    @Override
    public Type getGenericType() {
        return getType();
    }

    @Override
    public void setGenericType(Type genericType) {
        // Not supported
    }

    @Override
    public MediaType getMediaType() {
        return mediaType;
    }

    @Override
    public void setMediaType(MediaType mediaType) {
        this.mediaType = mediaType;
    }
}
