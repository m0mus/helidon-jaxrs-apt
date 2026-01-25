package io.helidon.examples.jaxrs.apt.test.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * Test resource for verifying @BeanParam functionality.
 */
@Path("/beanparam")
@Produces(MediaType.TEXT_PLAIN)
public class TestBeanParamResource {

    /**
     * Test @BeanParam with query and header parameters.
     */
    @GET
    @Path("/search")
    public String search(@BeanParam SearchParams params) {
        return params.toString();
    }

    /**
     * Test @BeanParam with form parameters.
     */
    @POST
    @Path("/form")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public String submitForm(@BeanParam FormDataParams params) {
        return params.toString();
    }

    /**
     * Test @BeanParam combined with regular parameters.
     */
    @GET
    @Path("/combined/{id}")
    public String combined(@PathParam("id") Long id, @BeanParam SearchParams params) {
        return "id=" + id + "," + params.toString();
    }
}
