package io.helidon.examples.jaxrs.apt.test.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * Sub-resource class for testing sub-resource locators.
 * This class is returned by TestSubResourceLocatorResource methods.
 */
@Produces(MediaType.TEXT_PLAIN)
public class TestSubResource {

    private final Long parentId;
    private final String context;

    public TestSubResource(Long parentId) {
        this.parentId = parentId;
        this.context = null;
    }

    public TestSubResource(Long parentId, String context) {
        this.parentId = parentId;
        this.context = context;
    }

    /**
     * Get all items for parent.
     */
    @GET
    public String getItems() {
        return "items-for:" + parentId;
    }

    /**
     * Get specific item.
     */
    @GET
    @Path("/{itemId}")
    public String getItem(@PathParam("itemId") Long itemId) {
        return "item:" + parentId + "/" + itemId;
    }

    /**
     * Create item.
     */
    @POST
    public String createItem(String name) {
        return "created:" + parentId + "/" + name;
    }

    /**
     * Update item.
     */
    @PUT
    @Path("/{itemId}")
    public String updateItem(@PathParam("itemId") Long itemId, String name) {
        return "updated:" + parentId + "/" + itemId + "/" + name;
    }

    /**
     * Delete item.
     */
    @DELETE
    @Path("/{itemId}")
    public String deleteItem(@PathParam("itemId") Long itemId) {
        return "deleted:" + parentId + "/" + itemId;
    }

    /**
     * Get item with context.
     */
    @GET
    @Path("/context")
    public String getWithContext() {
        return "context:" + parentId + "/" + (context != null ? context : "none");
    }

    /**
     * Search items with query param.
     */
    @GET
    @Path("/search")
    public String searchItems(@QueryParam("q") String query) {
        return "search:" + parentId + "/" + (query != null ? query : "all");
    }
}
