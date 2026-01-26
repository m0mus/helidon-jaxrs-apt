package io.helidon.examples.jaxrs.apt.test.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * Test resource for verifying @MatrixParam functionality.
 * Matrix parameters are embedded in path segments: /path;param=value
 */
@Path("/matrix")
@Produces(MediaType.TEXT_PLAIN)
public class TestMatrixParamResource {

    /**
     * Test @MatrixParam with String type.
     * URL: /matrix/users;role=admin
     */
    @GET
    @Path("/users")
    public String matrixParamString(@MatrixParam("role") String role) {
        return "role:" + (role != null ? role : "null");
    }

    /**
     * Test @MatrixParam with @DefaultValue.
     * URL: /matrix/items or /matrix/items;sort=name
     */
    @GET
    @Path("/items")
    public String matrixParamDefault(@MatrixParam("sort") @DefaultValue("id") String sort) {
        return "sort:" + sort;
    }

    /**
     * Test @MatrixParam with numeric type.
     * URL: /matrix/page;num=5
     */
    @GET
    @Path("/page")
    public String matrixParamNumeric(@MatrixParam("num") Integer pageNum) {
        return "page:" + (pageNum != null ? pageNum : "null");
    }

    /**
     * Test multiple @MatrixParam.
     * URL: /matrix/filter;status=active;limit=10
     */
    @GET
    @Path("/filter")
    public String multipleMatrixParams(@MatrixParam("status") String status,
                                        @MatrixParam("limit") @DefaultValue("20") Integer limit) {
        return "status:" + (status != null ? status : "null") + ",limit:" + limit;
    }

    /**
     * Test @MatrixParam combined with @PathParam.
     * URL: /matrix/products/123;color=red
     */
    @GET
    @Path("/products/{id}")
    public String combinedWithPathParam(@PathParam("id") Long id,
                                         @MatrixParam("color") String color) {
        return "id:" + id + ",color:" + (color != null ? color : "null");
    }

    /**
     * Test @MatrixParam combined with @QueryParam.
     * URL: /matrix/search;category=books?q=java
     */
    @GET
    @Path("/search")
    public String combinedWithQueryParam(@MatrixParam("category") String category,
                                          @QueryParam("q") String query) {
        return "category:" + (category != null ? category : "null") + ",query:" + (query != null ? query : "null");
    }
}
