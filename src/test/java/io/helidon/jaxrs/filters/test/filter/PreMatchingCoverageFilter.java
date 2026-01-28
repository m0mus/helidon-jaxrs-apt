package io.helidon.jaxrs.filters.test.filter;

import io.helidon.jaxrs.filters.test.util.CoverageTracker;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.net.URI;

/**
 * Pre-matching filter used to exercise coverage paths.
 */
@Provider
@PreMatching
@Priority(55)
public class PreMatchingCoverageFilter implements ContainerRequestFilter {

    @Context
    private HttpHeaders httpHeaders;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String mode = headerValue(requestContext, "X-Coverage-Mode");
        if (!"pre".equals(mode)) {
            return;
        }

        try {
            CoverageTracker.record("pre.enter", "true");

        requestContext.setProperty("pre-prop", "value");
        CoverageTracker.record("pre.prop", String.valueOf(requestContext.getProperty("pre-prop")));
        CoverageTracker.record("pre.prop.count", String.valueOf(requestContext.getPropertyNames().size()));
        requestContext.removeProperty("pre-prop");
        CoverageTracker.record("pre.prop.removed", String.valueOf(requestContext.getProperty("pre-prop") == null));

        UriInfo originalInfo = requestContext.getUriInfo();
        recordUriInfo("pre.uri.original", originalInfo);

        requestContext.setRequestUri(URI.create("http://localhost/coverage/123?override=1&flag"));
        requestContext.setRequestUri(URI.create("http://localhost/"), URI.create("http://localhost/coverage/123?override=2"));
        requestContext.setMethod("GET");

        UriInfo modifiedInfo = requestContext.getUriInfo();
        recordUriInfo("pre.uri.modified", modifiedInfo);

        requestContext.getHeaders().add("X-Pre-Added", "yes");
        CoverageTracker.record("pre.header.added", headerValue(requestContext, "X-Pre-Added"));
        CoverageTracker.record("pre.accept.count", String.valueOf(requestContext.getAcceptableMediaTypes().size()));
        CoverageTracker.record("pre.language.count", String.valueOf(requestContext.getAcceptableLanguages().size()));
        CoverageTracker.record("pre.cookie", requestContext.getCookies().get("session").getValue());
        CoverageTracker.record("pre.length", String.valueOf(requestContext.getLength()));
        CoverageTracker.record("pre.mediaType", String.valueOf(requestContext.getMediaType()));
        CoverageTracker.record("pre.language", String.valueOf(requestContext.getLanguage()));
        CoverageTracker.record("pre.hasEntity", String.valueOf(requestContext.hasEntity()));

        httpHeaders.getRequestHeader("Accept");
        httpHeaders.getRequestHeaders();
        httpHeaders.getAcceptableMediaTypes();
        httpHeaders.getAcceptableLanguages();
        httpHeaders.getMediaType();
        httpHeaders.getLanguage();
        httpHeaders.getCookies();
        httpHeaders.getDate();
        httpHeaders.getLength();

        try {
            requestContext.getRequest();
        } catch (UnsupportedOperationException ex) {
            CoverageTracker.record("pre.getRequest", "unsupported");
        }

        try {
            requestContext.setEntityStream(new java.io.ByteArrayInputStream(new byte[0]));
            CoverageTracker.record("pre.setEntityStream", "supported");
        } catch (UnsupportedOperationException ex) {
            CoverageTracker.record("pre.setEntityStream", "unsupported");
        }

        try (var stream = requestContext.getEntityStream()) {
            if (stream != null) {
                stream.readAllBytes();
            }
        }

        SecurityContext customSecurity = new SecurityContext() {
            @Override
            public java.security.Principal getUserPrincipal() {
                return () -> "pre-user";
            }

            @Override
            public boolean isUserInRole(String role) {
                return "role-pre".equals(role);
            }

            @Override
            public boolean isSecure() {
                return false;
            }

            @Override
            public String getAuthenticationScheme() {
                return "CUSTOM";
            }
        };
        requestContext.setSecurityContext(customSecurity);
            CoverageTracker.record("pre.security.scheme", requestContext.getSecurityContext().getAuthenticationScheme());
        } catch (Exception ex) {
            CoverageTracker.record("pre.error", ex.getClass().getSimpleName());
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

    private void recordUriInfo(String prefix, UriInfo uriInfo) {
        CoverageTracker.record(prefix + ".path", uriInfo.getPath());
        CoverageTracker.record(prefix + ".path.raw", uriInfo.getPath(false));
        CoverageTracker.record(prefix + ".segments", String.valueOf(uriInfo.getPathSegments().size()));
        CoverageTracker.record(prefix + ".segments.raw", String.valueOf(uriInfo.getPathSegments(false).size()));
        CoverageTracker.record(prefix + ".request", uriInfo.getRequestUri().toString());
        CoverageTracker.record(prefix + ".absolute", uriInfo.getAbsolutePath().toString());
        CoverageTracker.record(prefix + ".base", uriInfo.getBaseUri().toString());
        uriInfo.getRequestUriBuilder();
        uriInfo.getAbsolutePathBuilder();
        uriInfo.getBaseUriBuilder();
        uriInfo.getPathParameters();
        uriInfo.getPathParameters(false);
        uriInfo.getQueryParameters();
        uriInfo.getQueryParameters(false);
        uriInfo.getMatchedURIs();
        uriInfo.getMatchedURIs(false);
        uriInfo.getMatchedResources();
        uriInfo.resolve(URI.create("child"));
        uriInfo.relativize(URI.create("http://localhost/"));
        if (!uriInfo.getPathSegments().isEmpty()) {
            uriInfo.getPathSegments().getFirst().getMatrixParameters();
            uriInfo.getPathSegments().getFirst().getPath();
        }
    }
}

