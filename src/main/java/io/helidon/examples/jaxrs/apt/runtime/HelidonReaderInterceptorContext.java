package io.helidon.examples.jaxrs.apt.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.helidon.webserver.http.ServerRequest;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.ReaderInterceptorContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of ReaderInterceptorContext for intercepting request body reading.
 */
public class HelidonReaderInterceptorContext implements ReaderInterceptorContext {

    private final ObjectMapper objectMapper;
    private final Class<?> type;
    private final Map<String, Object> properties = new HashMap<>();
    private final MultivaluedMap<String, String> headers;
    private final String contentType;
    private byte[] content;
    private InputStream inputStream;
    private Object entity;
    private boolean proceeded = false;

    public HelidonReaderInterceptorContext(ServerRequest request, ObjectMapper objectMapper, Class<?> type) {
        this.objectMapper = objectMapper;
        this.type = type;
        // Read content once and cache it
        this.content = request.content().as(byte[].class);
        this.inputStream = new ByteArrayInputStream(content);
        // Cache headers
        this.headers = new MultivaluedHashMap<>();
        for (var header : request.headers()) {
            headers.add(header.name(), header.values());
        }
        this.contentType = request.headers().first(io.helidon.http.HeaderNames.CONTENT_TYPE)
                .map(Object::toString)
                .orElse("application/json");
    }

    @Override
    public Object proceed() throws IOException {
        if (!proceeded) {
            proceeded = true;
            entity = objectMapper.readValue(content, type);
        }
        return entity;
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public void setInputStream(InputStream is) {
        this.inputStream = is;
    }

    @Override
    public MultivaluedMap<String, String> getHeaders() {
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
        return type;
    }

    @Override
    public void setType(Class<?> type) {
        // Not supported in this implementation
    }

    @Override
    public Type getGenericType() {
        return type;
    }

    @Override
    public void setGenericType(Type genericType) {
        // Not supported
    }

    @Override
    public MediaType getMediaType() {
        return MediaType.valueOf(contentType);
    }

    @Override
    public void setMediaType(MediaType mediaType) {
        // Not supported
    }
}
