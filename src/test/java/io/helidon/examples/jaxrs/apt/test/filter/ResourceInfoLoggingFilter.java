package io.helidon.examples.jaxrs.apt.test.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Post-matching filter that uses @Context ResourceInfo to access the matched resource method.
 * This demonstrates that filters can inspect method annotations for authorization, auditing, etc.
 */
@Provider
@Priority(500)
public class ResourceInfoLoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Context
    private ResourceInfo resourceInfo;

    // Static fields to capture what ResourceInfo provides (for testing)
    private static Class<?> lastResourceClass;
    private static Method lastResourceMethod;
    private static String lastMethodAnnotations;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (resourceInfo != null) {
            lastResourceClass = resourceInfo.getResourceClass();
            lastResourceMethod = resourceInfo.getResourceMethod();

            // Capture method annotations as a string for testing
            if (lastResourceMethod != null) {
                StringBuilder sb = new StringBuilder();
                for (Annotation ann : lastResourceMethod.getAnnotations()) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(ann.annotationType().getSimpleName());
                }
                lastMethodAnnotations = sb.toString();
            }

            // Add header with resource info for testing
            // Note: We can't modify request headers in post-matching, but we can store in property
            requestContext.setProperty("X-Resource-Class", lastResourceClass.getSimpleName());
            requestContext.setProperty("X-Resource-Method", lastResourceMethod.getName());
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        // Add headers to response so tests can verify ResourceInfo was available
        if (lastResourceClass != null) {
            responseContext.getHeaders().add("X-Resource-Class", lastResourceClass.getSimpleName());
        }
        if (lastResourceMethod != null) {
            responseContext.getHeaders().add("X-Resource-Method", lastResourceMethod.getName());
        }
        if (lastMethodAnnotations != null && !lastMethodAnnotations.isEmpty()) {
            responseContext.getHeaders().add("X-Method-Annotations", lastMethodAnnotations);
        }
    }

    // Static accessors for testing
    public static Class<?> getLastResourceClass() {
        return lastResourceClass;
    }

    public static Method getLastResourceMethod() {
        return lastResourceMethod;
    }

    public static String getLastMethodAnnotations() {
        return lastMethodAnnotations;
    }

    public static void reset() {
        lastResourceClass = null;
        lastResourceMethod = null;
        lastMethodAnnotations = null;
    }
}
