package io.helidon.examples.jaxrs.apt.runtime;

import io.helidon.webserver.http.ServerRequest;
import jakarta.ws.rs.core.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper that adapts Helidon's ServerRequest to JAX-RS UriInfo.
 */
public class HelidonUriInfo implements UriInfo {

    private final ServerRequest request;

    public HelidonUriInfo(ServerRequest request) {
        this.request = request;
    }

    @Override
    public String getPath() {
        return request.path().path();
    }

    @Override
    public String getPath(boolean decode) {
        return getPath();
    }

    @Override
    public List<PathSegment> getPathSegments() {
        return getPathSegments(true);
    }

    @Override
    public List<PathSegment> getPathSegments(boolean decode) {
        List<PathSegment> segments = new ArrayList<>();
        String path = getPath();
        for (String segment : path.split("/")) {
            if (!segment.isEmpty()) {
                segments.add(new SimplePathSegment(segment));
            }
        }
        return segments;
    }

    @Override
    public URI getRequestUri() {
        return request.requestedUri().toUri();
    }

    @Override
    public UriBuilder getRequestUriBuilder() {
        return UriBuilder.fromUri(getRequestUri());
    }

    @Override
    public URI getAbsolutePath() {
        return request.requestedUri().toUri();
    }

    @Override
    public UriBuilder getAbsolutePathBuilder() {
        return UriBuilder.fromUri(getAbsolutePath());
    }

    @Override
    public URI getBaseUri() {
        var uri = request.requestedUri();
        return URI.create(uri.scheme() + "://" + uri.host() + ":" + uri.port() + "/");
    }

    @Override
    public UriBuilder getBaseUriBuilder() {
        return UriBuilder.fromUri(getBaseUri());
    }

    @Override
    public MultivaluedMap<String, String> getPathParameters() {
        return getPathParameters(true);
    }

    @Override
    public MultivaluedMap<String, String> getPathParameters(boolean decode) {
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        request.path().pathParameters().toMap().forEach((key, values) -> {
            for (String value : values) {
                params.add(key, value);
            }
        });
        return params;
    }

    @Override
    public MultivaluedMap<String, String> getQueryParameters() {
        return getQueryParameters(true);
    }

    @Override
    public MultivaluedMap<String, String> getQueryParameters(boolean decode) {
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        request.query().toMap().forEach((key, values) -> {
            for (String value : values) {
                params.add(key, value);
            }
        });
        return params;
    }

    @Override
    public List<String> getMatchedURIs() {
        return List.of(getPath());
    }

    @Override
    public List<String> getMatchedURIs(boolean decode) {
        return getMatchedURIs();
    }

    @Override
    public List<Object> getMatchedResources() {
        return List.of();
    }

    @Override
    public URI resolve(URI uri) {
        return getBaseUri().resolve(uri);
    }

    @Override
    public URI relativize(URI uri) {
        return getBaseUri().relativize(uri);
    }

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
