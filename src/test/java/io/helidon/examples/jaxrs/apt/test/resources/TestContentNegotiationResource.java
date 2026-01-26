package io.helidon.examples.jaxrs.apt.test.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * Test resource for verifying content negotiation.
 * Tests @Consumes and @Produces with Accept/Content-Type headers.
 */
@Path("/content")
public class TestContentNegotiationResource {

    /**
     * Endpoint that only produces JSON.
     */
    @GET
    @Path("/json")
    @Produces(MediaType.APPLICATION_JSON)
    public String jsonOnly() {
        return "{\"format\":\"json\"}";
    }

    /**
     * Endpoint that only produces XML.
     */
    @GET
    @Path("/xml")
    @Produces(MediaType.APPLICATION_XML)
    public String xmlOnly() {
        return "<format>xml</format>";
    }

    /**
     * Endpoint that produces JSON or XML.
     */
    @GET
    @Path("/both")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public String jsonOrXml() {
        return "both";
    }

    /**
     * Endpoint that produces plain text.
     */
    @GET
    @Path("/text")
    @Produces(MediaType.TEXT_PLAIN)
    public String textOnly() {
        return "plain text";
    }

    /**
     * Endpoint that only consumes JSON.
     */
    @POST
    @Path("/accept-json")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public String acceptJson(String body) {
        return "received:" + body;
    }

    /**
     * Endpoint that only consumes XML.
     */
    @POST
    @Path("/accept-xml")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.TEXT_PLAIN)
    public String acceptXml(String body) {
        return "received:" + body;
    }

    /**
     * Endpoint that consumes JSON or XML.
     */
    @POST
    @Path("/accept-both")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces(MediaType.TEXT_PLAIN)
    public String acceptBoth(String body) {
        return "received:" + body;
    }

    /**
     * Endpoint that consumes form data.
     */
    @POST
    @Path("/accept-form")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public String acceptForm(@FormParam("data") String data) {
        return "form:" + data;
    }

    /**
     * Endpoint with both @Consumes and @Produces.
     */
    @PUT
    @Path("/echo")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String echoJson(String body) {
        return body;
    }

    /**
     * Endpoint with no content type restrictions.
     */
    @GET
    @Path("/any")
    public String anyContentType() {
        return "any";
    }

    /**
     * POST endpoint with no @Consumes (should accept anything).
     */
    @POST
    @Path("/post-any")
    @Produces(MediaType.TEXT_PLAIN)
    public String postAny(String body) {
        return "body:" + body;
    }

    /**
     * Endpoint with wildcard media type.
     */
    @GET
    @Path("/wildcard")
    @Produces("text/*")
    public String wildcardProduces() {
        return "wildcard";
    }
}
