package io.helidon.examples.jaxrs.apt.test.filter;

import io.helidon.examples.jaxrs.apt.test.resources.CustomBusinessException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * ExceptionMapper for CustomBusinessException.
 * Returns a 422 Unprocessable Entity with error details.
 */
@Provider
public class CustomBusinessExceptionMapper implements ExceptionMapper<CustomBusinessException> {

    @Override
    public Response toResponse(CustomBusinessException exception) {
        String json = "{\"error\":\"" + exception.getMessage() + "\",\"code\":\"" + exception.getErrorCode() + "\"}";
        return Response.status(422)
                .header("X-Error-Code", exception.getErrorCode())
                .entity(json)
                .build();
    }
}
