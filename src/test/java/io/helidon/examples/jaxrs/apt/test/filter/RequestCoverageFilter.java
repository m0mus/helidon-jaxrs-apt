package io.helidon.examples.jaxrs.apt.test.filter;

import io.helidon.examples.jaxrs.apt.runtime.HelidonContainerRequestContext;
import io.helidon.examples.jaxrs.apt.test.util.CoverageTracker;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.net.URI;

/**
 * Post-matching request filter used to exercise coverage paths.
 */
@Provider
@Priority(110)
public class RequestCoverageFilter implements ContainerRequestFilter {

    @Context
    private UriInfo uriInfo;

    @Context
    private SecurityContext securityContext;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String mode = headerValue(requestContext, "X-Coverage-Mode");
        if (!"request".equals(mode)) {
            return;
        }

        try {
            CoverageTracker.record("request.method", requestContext.getMethod());
            CoverageTracker.record("request.header", headerValue(requestContext, "X-Coverage-Header"));
            CoverageTracker.record("request.accept.count", String.valueOf(requestContext.getAcceptableMediaTypes().size()));
            CoverageTracker.record("request.language.count", String.valueOf(requestContext.getAcceptableLanguages().size()));
            CoverageTracker.record("request.cookies.count", String.valueOf(requestContext.getCookies().size()));
            CoverageTracker.record("request.mediaType", String.valueOf(requestContext.getMediaType()));
            CoverageTracker.record("request.language", String.valueOf(requestContext.getLanguage()));
            CoverageTracker.record("request.length", String.valueOf(requestContext.getLength()));
            CoverageTracker.record("request.hasEntity", String.valueOf(requestContext.hasEntity()));

            requestContext.setProperty("request-prop", "value");
            CoverageTracker.record("request.prop", String.valueOf(requestContext.getProperty("request-prop")));
            CoverageTracker.record("request.prop.count", String.valueOf(requestContext.getPropertyNames().size()));
            requestContext.removeProperty("request-prop");
            CoverageTracker.record("request.prop.removed", String.valueOf(requestContext.getProperty("request-prop") == null));

            recordUriInfo("request.uri", uriInfo);

            CoverageTracker.record("request.security.scheme", securityContext.getAuthenticationScheme());
            CoverageTracker.record("request.security.user",
                    securityContext.getUserPrincipal() == null ? "null" : securityContext.getUserPrincipal().getName());
            CoverageTracker.record("request.security.role", String.valueOf(securityContext.isUserInRole("admin")));
            CoverageTracker.record("request.security.secure", String.valueOf(securityContext.isSecure()));

            String authMode = headerValue(requestContext, "X-Coverage-Auth");
            if (authMode != null) {
                String prefix = "auth." + authMode;
                CoverageTracker.record(prefix + ".scheme", securityContext.getAuthenticationScheme());
                CoverageTracker.record(prefix + ".user",
                        securityContext.getUserPrincipal() == null ? "null" : securityContext.getUserPrincipal().getName());
                CoverageTracker.record(prefix + ".role", String.valueOf(securityContext.isUserInRole("admin")));
                CoverageTracker.record(prefix + ".secure", String.valueOf(securityContext.isSecure()));
            }

            try {
                requestContext.getRequest();
            } catch (UnsupportedOperationException ex) {
                CoverageTracker.record("request.getRequest", "unsupported");
            }

            try {
                requestContext.setRequestUri(URI.create("http://localhost/coverage/123"));
            } catch (UnsupportedOperationException ex) {
                CoverageTracker.record("request.setRequestUri", "unsupported");
            }

            try {
                requestContext.setRequestUri(URI.create("http://localhost/"), URI.create("http://localhost/coverage/123"));
            } catch (UnsupportedOperationException ex) {
                CoverageTracker.record("request.setRequestUri.base", "unsupported");
            }

            try {
                requestContext.setMethod("POST");
            } catch (UnsupportedOperationException ex) {
                CoverageTracker.record("request.setMethod", "unsupported");
            }

            try {
                requestContext.setEntityStream(new java.io.ByteArrayInputStream(new byte[0]));
            } catch (UnsupportedOperationException ex) {
                CoverageTracker.record("request.setEntityStream", "unsupported");
            }

            try (var stream = requestContext.getEntityStream()) {
                if (stream != null) {
                    stream.readAllBytes();
                }
            }

            if (requestContext instanceof HelidonContainerRequestContext helidonContext) {
                CoverageTracker.record("request.resourceInfo.exists", String.valueOf(helidonContext.getResourceInfo() != null));
                ResourceInfo info = new ResourceInfo() {
                    @Override
                    public java.lang.reflect.Method getResourceMethod() {
                        return null;
                    }

                    @Override
                    public Class<?> getResourceClass() {
                        return RequestCoverageFilter.class;
                    }
                };
                helidonContext.setResourceInfo(info);
                CoverageTracker.record("request.resourceInfo.class",
                        helidonContext.getResourceInfo().getResourceClass().getName());
                CoverageTracker.record("request.resourceInfo.property",
                        String.valueOf(helidonContext.getProperty(HelidonContainerRequestContext.RESOURCE_INFO_PROPERTY) != null));
            }
        } catch (Exception ex) {
            CoverageTracker.record("request.error", ex.getClass().getSimpleName());
        }
    }

    private void recordUriInfo(String prefix, UriInfo info) {
        CoverageTracker.record(prefix + ".path", info.getPath());
        CoverageTracker.record(prefix + ".path.raw", info.getPath(false));
        CoverageTracker.record(prefix + ".segments", String.valueOf(info.getPathSegments().size()));
        CoverageTracker.record(prefix + ".segments.raw", String.valueOf(info.getPathSegments(false).size()));
        CoverageTracker.record(prefix + ".request", info.getRequestUri().toString());
        CoverageTracker.record(prefix + ".absolute", info.getAbsolutePath().toString());
        CoverageTracker.record(prefix + ".base", info.getBaseUri().toString());
        info.getRequestUriBuilder();
        info.getAbsolutePathBuilder();
        info.getBaseUriBuilder();
        info.getPathParameters();
        info.getPathParameters(false);
        info.getQueryParameters();
        info.getQueryParameters(false);
        info.getMatchedURIs();
        info.getMatchedURIs(false);
        info.getMatchedResources();
        info.resolve(URI.create("child"));
        info.relativize(URI.create("http://localhost/"));
        if (!info.getPathSegments().isEmpty()) {
            info.getPathSegments().getFirst().getMatrixParameters();
            info.getPathSegments().getFirst().getPath();
        }
    }

    private String headerValue(ContainerRequestContext requestContext, String name) {
        for (var entry : requestContext.getHeaders().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                if (!entry.getValue().isEmpty()) {
                    return entry.getValue().getFirst();
                }
            }
        }
        return null;
    }
}
