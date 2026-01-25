package io.helidon.examples.jaxrs.apt.test.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * Test resource for verifying ExceptionMapper functionality.
 */
@Path("/exceptions")
@Produces(MediaType.APPLICATION_JSON)
public class TestExceptionResource {

    /**
     * Throws a CustomBusinessException.
     */
    @GET
    @Path("/business")
    public String throwBusinessException() {
        throw new CustomBusinessException("Business rule violated", "BIZ001");
    }

    /**
     * Throws a ValidationException.
     */
    @GET
    @Path("/validation")
    public String throwValidationException() {
        throw new ValidationException("Invalid email format", "email");
    }

    /**
     * Throws a standard NotFoundException (should use default handling).
     */
    @GET
    @Path("/notfound")
    public String throwNotFoundException() {
        throw new NotFoundException("Resource not found");
    }

    /**
     * Throws a generic RuntimeException (should use default 500 handling).
     */
    @GET
    @Path("/runtime")
    public String throwRuntimeException() {
        throw new RuntimeException("Unexpected error occurred");
    }

    /**
     * Normal endpoint that doesn't throw.
     */
    @GET
    @Path("/ok")
    @Produces(MediaType.TEXT_PLAIN)
    public String ok() {
        return "OK";
    }
}
