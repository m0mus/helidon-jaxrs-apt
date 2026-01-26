package io.helidon.examples.jaxrs.apt.test.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Set;

/**
 * Test resource for verifying List/Set parameter support.
 */
@Path("/collections")
@Produces(MediaType.TEXT_PLAIN)
public class TestCollectionParamResource {

    /**
     * Test List<String> query parameter.
     */
    @GET
    @Path("/query/list")
    public String queryParamList(@QueryParam("tag") List<String> tags) {
        return "tags:" + String.join(",", tags);
    }

    /**
     * Test Set<String> query parameter (duplicates removed).
     */
    @GET
    @Path("/query/set")
    public String queryParamSet(@QueryParam("tag") Set<String> tags) {
        return "tags:" + tags.size() + ":" + String.join(",", tags.stream().sorted().toList());
    }

    /**
     * Test List<Integer> query parameter with type conversion.
     */
    @GET
    @Path("/query/list/int")
    public String queryParamListInt(@QueryParam("id") List<Integer> ids) {
        int sum = ids.stream().mapToInt(Integer::intValue).sum();
        return "ids:" + ids.size() + ",sum:" + sum;
    }

    /**
     * Test List<Long> query parameter with type conversion.
     */
    @GET
    @Path("/query/list/long")
    public String queryParamListLong(@QueryParam("id") List<Long> ids) {
        long sum = ids.stream().mapToLong(Long::longValue).sum();
        return "ids:" + ids.size() + ",sum:" + sum;
    }

    /**
     * Test List<String> header parameter.
     */
    @GET
    @Path("/header/list")
    public String headerParamList(@HeaderParam("X-Tags") List<String> tags) {
        return "headers:" + String.join(",", tags);
    }

    /**
     * Test Set<String> header parameter.
     */
    @GET
    @Path("/header/set")
    public String headerParamSet(@HeaderParam("X-Tags") Set<String> tags) {
        return "headers:" + tags.size() + ":" + String.join(",", tags.stream().sorted().toList());
    }

    /**
     * Test combined List parameters with regular params.
     */
    @GET
    @Path("/combined/{category}")
    public String combinedParams(@PathParam("category") String category,
                                  @QueryParam("tag") List<String> tags,
                                  @QueryParam("limit") Integer limit) {
        return "category:" + category + ",tags:" + String.join(",", tags) + ",limit:" + limit;
    }

    /**
     * Test empty list (no values provided).
     */
    @GET
    @Path("/query/empty")
    public String queryParamEmpty(@QueryParam("tag") List<String> tags) {
        return "count:" + tags.size();
    }
}
