package io.helidon.examples.jaxrs.apt.test.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * Test resource for verifying parameter extraction from various JAX-RS annotations.
 */
@Path("/params")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TestParameterResource {

    /**
     * Test @PathParam with String type.
     */
    @GET
    @Path("/path/{value}")
    @Produces(MediaType.TEXT_PLAIN)
    public String pathParamString(@PathParam("value") String value) {
        return "path:" + value;
    }

    /**
     * Test @PathParam with Long type.
     */
    @GET
    @Path("/path/numeric/{id}")
    @Produces(MediaType.TEXT_PLAIN)
    public String pathParamNumeric(@PathParam("id") Long id) {
        return "id:" + id;
    }

    /**
     * Test @QueryParam.
     */
    @GET
    @Path("/query")
    @Produces(MediaType.TEXT_PLAIN)
    public String queryParam(@QueryParam("q") String query) {
        return "query:" + (query != null ? query : "null");
    }

    /**
     * Test @QueryParam with @DefaultValue.
     */
    @GET
    @Path("/query/default")
    @Produces(MediaType.TEXT_PLAIN)
    public String queryParamWithDefault(@QueryParam("q") @DefaultValue("defaultValue") String query) {
        return "query:" + query;
    }

    /**
     * Test multiple @QueryParam.
     */
    @GET
    @Path("/query/multiple")
    @Produces(MediaType.TEXT_PLAIN)
    public String multipleQueryParams(@QueryParam("a") String a,
                                       @QueryParam("b") @DefaultValue("defaultB") String b) {
        return "a:" + (a != null ? a : "null") + ",b:" + b;
    }

    /**
     * Test @HeaderParam.
     */
    @GET
    @Path("/header")
    @Produces(MediaType.TEXT_PLAIN)
    public String headerParam(@HeaderParam("X-Custom-Header") String headerValue) {
        return "header:" + (headerValue != null ? headerValue : "null");
    }

    /**
     * Test @HeaderParam with @DefaultValue.
     */
    @GET
    @Path("/header/default")
    @Produces(MediaType.TEXT_PLAIN)
    public String headerParamWithDefault(@HeaderParam("X-Custom-Header") @DefaultValue("defaultHeader") String headerValue) {
        return "header:" + headerValue;
    }

    /**
     * Test @CookieParam.
     */
    @GET
    @Path("/cookie")
    @Produces(MediaType.TEXT_PLAIN)
    public String cookieParam(@CookieParam("session") String sessionId) {
        return "cookie:" + (sessionId != null ? sessionId : "null");
    }

    /**
     * Test @CookieParam with @DefaultValue.
     */
    @GET
    @Path("/cookie/default")
    @Produces(MediaType.TEXT_PLAIN)
    public String cookieParamWithDefault(@CookieParam("session") @DefaultValue("defaultSession") String sessionId) {
        return "cookie:" + sessionId;
    }

    /**
     * Test @FormParam.
     */
    @POST
    @Path("/form")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public String formParam(@FormParam("field") String field) {
        return "form:" + (field != null ? field : "null");
    }

    /**
     * Test multiple @FormParam.
     */
    @POST
    @Path("/form/multiple")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public String multipleFormParams(@FormParam("name") String name,
                                      @FormParam("email") String email) {
        return "name:" + (name != null ? name : "null") + ",email:" + (email != null ? email : "null");
    }

    /**
     * Test @Context UriInfo.
     */
    @GET
    @Path("/context/uriinfo")
    @Produces(MediaType.TEXT_PLAIN)
    public String contextUriInfo(@Context UriInfo uriInfo) {
        return "path:" + uriInfo.getPath();
    }

    /**
     * Test @Context HttpHeaders.
     */
    @GET
    @Path("/context/headers")
    @Produces(MediaType.TEXT_PLAIN)
    public String contextHeaders(@Context HttpHeaders headers) {
        String host = headers.getHeaderString("Host");
        return "host:" + (host != null ? host : "null");
    }

    /**
     * Test combined parameters.
     */
    @GET
    @Path("/combined/{id}")
    @Produces(MediaType.TEXT_PLAIN)
    public String combinedParams(@PathParam("id") Long id,
                                  @QueryParam("filter") String filter,
                                  @HeaderParam("X-Token") String token) {
        return "id:" + id + ",filter:" + (filter != null ? filter : "null") + ",token:" + (token != null ? token : "null");
    }

    // ==================== Response Return Type Tests ====================

    /**
     * Test Response with custom status code.
     */
    @GET
    @Path("/response/status")
    @Produces(MediaType.TEXT_PLAIN)
    public Response responseWithStatus() {
        return Response.status(201)
                .entity("created")
                .build();
    }

    /**
     * Test simple Response - most basic case.
     */
    @GET
    @Path("/response/simple")
    @Produces(MediaType.TEXT_PLAIN)
    public Response responseSimple() {
        return Response.ok("hello").build();
    }

    /**
     * Test Response with JSON entity.
     */
    @GET
    @Path("/response/json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response responseWithJson() {
        return Response.ok(new ResponseData("test", 42)).build();
    }

    /**
     * Test Response with custom headers.
     */
    @GET
    @Path("/response/headers")
    @Produces(MediaType.TEXT_PLAIN)
    public Response responseWithHeaders() {
        return Response.ok("data")
                .header("X-Custom-Response", "custom-value")
                .header("X-Request-Id", "12345")
                .build();
    }

    /**
     * Test Response with no entity (204).
     */
    @DELETE
    @Path("/response/nocontent")
    public Response responseNoContent() {
        return Response.noContent().build();
    }

    /**
     * Test Response with error status.
     */
    @GET
    @Path("/response/error")
    @Produces(MediaType.TEXT_PLAIN)
    public Response responseError() {
        return Response.status(400).entity("Bad request data").build();
    }

    /**
     * Simple data class for JSON response testing.
     */
    public record ResponseData(String name, int value) {}
}
