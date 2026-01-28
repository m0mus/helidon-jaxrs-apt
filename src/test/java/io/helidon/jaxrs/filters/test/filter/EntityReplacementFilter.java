package io.helidon.jaxrs.filters.test.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Test filter that replaces the request entity.
 */
@Priority(1000)
public class EntityReplacementFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if ("true".equals(requestContext.getHeaderString("X-Replace-Entity"))) {
            // Replace entity with modified content
            String original = new String(requestContext.getEntityStream().readAllBytes(), StandardCharsets.UTF_8);
            String modified = original.toUpperCase();
            requestContext.setEntityStream(
                new ByteArrayInputStream(modified.getBytes(StandardCharsets.UTF_8))
            );
        }
    }
}
