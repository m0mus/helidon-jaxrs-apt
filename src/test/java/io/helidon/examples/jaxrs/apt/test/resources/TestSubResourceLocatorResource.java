package io.helidon.examples.jaxrs.apt.test.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * Test resource for verifying sub-resource locator functionality.
 * Methods with @Path but no HTTP method annotation return sub-resource instances.
 */
@Path("/parents")
@Produces(MediaType.TEXT_PLAIN)
public class TestSubResourceLocatorResource {

    /**
     * Regular endpoint on parent resource.
     */
    @GET
    public String listParents() {
        return "all-parents";
    }

    /**
     * Get specific parent.
     */
    @GET
    @Path("/{parentId}")
    public String getParent(@PathParam("parentId") Long parentId) {
        return "parent:" + parentId;
    }

    /**
     * Sub-resource locator - returns TestSubResource for items.
     * URL: /parents/{parentId}/items/...
     */
    @Path("/{parentId}/items")
    public TestSubResource getItems(@PathParam("parentId") Long parentId) {
        return new TestSubResource(parentId);
    }

    /**
     * Sub-resource locator with additional context parameter.
     * URL: /parents/{parentId}/ctx/{ctx}/items/...
     */
    @Path("/{parentId}/ctx/{ctx}/items")
    public TestSubResource getItemsWithContext(@PathParam("parentId") Long parentId,
                                                @PathParam("ctx") String context) {
        return new TestSubResource(parentId, context);
    }

    /**
     * Another regular endpoint after locators.
     */
    @GET
    @Path("/count")
    public String countParents() {
        return "count:100";
    }
}
