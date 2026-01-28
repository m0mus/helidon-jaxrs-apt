package io.helidon.jaxrs.filters.test.filter;

import io.helidon.jaxrs.filters.test.util.FilterOrderTracker;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

/**
 * Pre-matching filter that verifies @Context injection is available and working.
 * This tests that UriInfo, HttpHeaders, and SecurityContext proxies can delegate
 * to the actual request-scoped objects during pre-matching phase.
 *
 * <p>Note: ResourceInfo is intentionally included to verify it's NOT available
 * in pre-matching filters (since route matching hasn't happened yet).
 */
@Provider
@PreMatching
@Priority(60)
public class PreMatchingContextFilter implements ContainerRequestFilter {

    @Context
    private UriInfo uriInfo;

    @Context
    private HttpHeaders httpHeaders;

    @Context
    private SecurityContext securityContext;

    @Context
    private ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // Check proxies are injected
        if (uriInfo == null || httpHeaders == null || securityContext == null) {
            FilterOrderTracker.recordRequestFilter("PreMatchingContextFilter:NULL_PROXY");
            return;
        }

        // Check if detailed context test is requested
        String testHeader = httpHeaders.getHeaderString("X-Test-PreMatching-Context");
        if (testHeader == null) {
            // Basic test - just verify proxies work
            try {
                uriInfo.getPath();
                httpHeaders.getHeaderString("Host");
                securityContext.isSecure();
                FilterOrderTracker.recordRequestFilter("PreMatchingContextFilter:OK");
            } catch (Exception e) {
                FilterOrderTracker.recordRequestFilter("PreMatchingContextFilter:ERROR:" + e.getMessage());
            }
            return;
        }

        // Detailed test - record all context values for verification
        StringBuilder result = new StringBuilder("PreMatchingContextFilter:");

        // Test UriInfo
        try {
            String path = uriInfo.getPath();
            result.append("path=").append(path).append(";");
        } catch (Exception e) {
            result.append("path=ERROR:").append(e.getClass().getSimpleName()).append(";");
        }

        // Test HttpHeaders - read custom test header value
        try {
            String customHeader = httpHeaders.getHeaderString("X-Custom-Test-Header");
            result.append("customHeader=").append(customHeader != null ? customHeader : "null").append(";");
        } catch (Exception e) {
            result.append("customHeader=ERROR:").append(e.getClass().getSimpleName()).append(";");
        }

        // Test SecurityContext
        try {
            boolean secure = securityContext.isSecure();
            result.append("secure=").append(secure).append(";");
        } catch (Exception e) {
            result.append("secure=ERROR:").append(e.getClass().getSimpleName()).append(";");
        }

        // Test ResourceInfo - should NOT be available in pre-matching
        if (resourceInfo == null) {
            result.append("resourceInfo=NULL_PROXY;");
        } else {
            try {
                // This should throw because ResourceInfo is not registered yet
                resourceInfo.getResourceMethod();
                result.append("resourceInfo=UNEXPECTED_SUCCESS;");
            } catch (IllegalStateException e) {
                // Expected - ResourceInfo not available in pre-matching
                result.append("resourceInfo=NOT_AVAILABLE;");
            } catch (Exception e) {
                result.append("resourceInfo=ERROR:").append(e.getClass().getSimpleName()).append(";");
            }
        }

        FilterOrderTracker.recordRequestFilter(result.toString());
    }
}

