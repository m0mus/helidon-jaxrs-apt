package io.helidon.examples.jaxrs.apt.test.comparison;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * ExceptionMapper for NotFoundException that includes the error message in the response body.
 * This ensures Jersey behaves the same as the APT implementation for comparison testing.
 */
@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {

    @Override
    public Response toResponse(NotFoundException exception) {
        String message = exception.getMessage();
        if (message == null || message.isEmpty()) {
            message = "Not Found";
        }
        return Response.status(404)
                .entity(message)
                .type("text/plain")
                .build();
    }
}
