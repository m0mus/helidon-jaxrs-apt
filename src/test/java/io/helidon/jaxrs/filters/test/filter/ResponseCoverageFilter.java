package io.helidon.jaxrs.filters.test.filter;

import io.helidon.jaxrs.filters.test.util.CoverageTracker;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

/**
 * Response filter used to exercise coverage paths.
 */
@Provider
@Priority(110)
public class ResponseCoverageFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        String mode = headerValue(requestContext, "X-Coverage-Mode");
        if (!"response".equals(mode)) {
            return;
        }

        responseContext.setStatus(201);
        responseContext.setStatusInfo(Response.Status.ACCEPTED);

        responseContext.getHeaders().add("X-Coverage-Response", "yes");
        responseContext.getStringHeaders().add("X-String-Header", "value");
        responseContext.getHeaders().add("X-Existing", "from-filter");

        CoverageTracker.record("response.status", String.valueOf(responseContext.getStatus()));
        CoverageTracker.record("response.statusInfo", responseContext.getStatusInfo().getReasonPhrase());
        CoverageTracker.record("response.header", responseContext.getHeaderString("X-String-Header"));
        CoverageTracker.record("response.allowed.count", String.valueOf(responseContext.getAllowedMethods().size()));
        CoverageTracker.record("response.date", String.valueOf(responseContext.getDate()));
        CoverageTracker.record("response.language", String.valueOf(responseContext.getLanguage()));
        CoverageTracker.record("response.length", String.valueOf(responseContext.getLength()));

        responseContext.setEntity("entity");
        responseContext.setEntity("entity2", new java.lang.annotation.Annotation[0], MediaType.TEXT_PLAIN_TYPE);

        CoverageTracker.record("response.mediaType", String.valueOf(responseContext.getMediaType()));
        CoverageTracker.record("response.cookies.count", String.valueOf(responseContext.getCookies().size()));
        CoverageTracker.record("response.entityTag", String.valueOf(responseContext.getEntityTag()));
        CoverageTracker.record("response.lastModified", String.valueOf(responseContext.getLastModified()));
        CoverageTracker.record("response.location", String.valueOf(responseContext.getLocation()));
        CoverageTracker.record("response.links.count", String.valueOf(responseContext.getLinks().size()));
        CoverageTracker.record("response.hasLink", String.valueOf(responseContext.hasLink("self")));
        CoverageTracker.record("response.link", String.valueOf(responseContext.getLink("self")));
        CoverageTracker.record("response.linkBuilder", String.valueOf(responseContext.getLinkBuilder("self")));

        CoverageTracker.record("response.hasEntity", String.valueOf(responseContext.hasEntity()));
        CoverageTracker.record("response.entity", String.valueOf(responseContext.getEntity()));
        CoverageTracker.record("response.entityClass", String.valueOf(responseContext.getEntityClass()));
        CoverageTracker.record("response.entityType", String.valueOf(responseContext.getEntityType()));
        CoverageTracker.record("response.entityAnnotations", String.valueOf(responseContext.getEntityAnnotations().length));

        try {
            responseContext.getEntityStream();
        } catch (UnsupportedOperationException ex) {
            CoverageTracker.record("response.getEntityStream", "unsupported");
        }

        try {
            responseContext.setEntityStream(new java.io.ByteArrayOutputStream());
        } catch (UnsupportedOperationException ex) {
            CoverageTracker.record("response.setEntityStream", "unsupported");
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

