package io.helidon.examples.jaxrs.apt.test.resources;

import io.helidon.examples.jaxrs.apt.test.filter.AuditBinding;
import io.helidon.examples.jaxrs.apt.test.util.FilterOrderTracker;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Test resource for verifying filter behavior.
 */
@Path("/filter")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TestFilterResource {

    /**
     * Normal endpoint for filter testing.
     * All global filters should execute on this endpoint.
     */
    @GET
    @Path("/test")
    @Produces(MediaType.TEXT_PLAIN)
    public String test() {
        return "filter-test";
    }

    /**
     * POST endpoint for method override testing.
     */
    @POST
    @Path("/test")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String testPost(String body) {
        return "created:" + body;
    }

    /**
     * PUT endpoint for method override testing.
     */
    @PUT
    @Path("/test")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String testPut(String body) {
        return "updated:" + body;
    }

    /**
     * DELETE endpoint for method override testing.
     */
    @DELETE
    @Path("/test")
    @Produces(MediaType.TEXT_PLAIN)
    public String testDelete() {
        return "deleted";
    }

    /**
     * Endpoint with @AuditBinding for name-bound filter testing.
     * Only filters with @AuditBinding should execute on this endpoint
     * (in addition to global filters).
     */
    @GET
    @Path("/audited")
    @AuditBinding
    @Produces(MediaType.TEXT_PLAIN)
    public String audited() {
        return "audited";
    }

    /**
     * Endpoint for testing filter abort capability.
     * The AbortFilter should abort this request and return a 403 status.
     */
    @GET
    @Path("/abort")
    @Produces(MediaType.TEXT_PLAIN)
    public String abort() {
        return "should-not-reach";
    }

    /**
     * Endpoint that returns the current filter execution order.
     * Useful for verifying filter execution in tests.
     */
    @GET
    @Path("/order")
    @Produces(MediaType.TEXT_PLAIN)
    public String getFilterOrder() {
        return String.join(",", FilterOrderTracker.getRequestFilterOrder());
    }

    /**
     * Endpoint for testing request body with reader interceptors.
     */
    @POST
    @Path("/body")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public String bodyEndpoint(TestBody body) {
        return "received:" + body.message();
    }

    /**
     * Endpoint that returns JSON for writer interceptor testing.
     */
    @GET
    @Path("/json")
    public TestBody jsonEndpoint() {
        return new TestBody("test-message");
    }

    /**
     * Endpoint for testing pre-matching filters.
     * Pre-matching filters execute before route matching.
     */
    @GET
    @Path("/prematching")
    @Produces(MediaType.TEXT_PLAIN)
    public String preMatching() {
        return "prematched";
    }

    /**
     * Endpoint that throws an exception to test error handling in filters.
     */
    @GET
    @Path("/error")
    @Produces(MediaType.TEXT_PLAIN)
    public String errorEndpoint() {
        throw new NotFoundException("Test not found error");
    }

    /**
     * Simple body class for JSON testing.
     */
    public record TestBody(String message) {
    }
}
