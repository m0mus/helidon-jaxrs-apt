package io.helidon.examples.jaxrs.apt.test.filter;

import io.helidon.examples.jaxrs.apt.test.resources.ValidationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * ExceptionMapper for ValidationException.
 * Returns a 400 Bad Request with validation error details.
 */
@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ValidationException> {

    @Override
    public Response toResponse(ValidationException exception) {
        String json = "{\"error\":\"" + exception.getMessage() + "\",\"field\":\"" + exception.getField() + "\"}";
        return Response.status(Response.Status.BAD_REQUEST)
                .header("X-Validation-Field", exception.getField())
                .entity(json)
                .build();
    }
}
