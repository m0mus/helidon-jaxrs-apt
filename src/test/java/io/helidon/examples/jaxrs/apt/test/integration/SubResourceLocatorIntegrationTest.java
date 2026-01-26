package io.helidon.examples.jaxrs.apt.test.integration;

import io.helidon.examples.jaxrs.apt.test.resources.TestSubResourceLocatorResource$$JaxRsRouting;
import io.helidon.http.HeaderNames;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.http.HttpRouting;
import org.junit.jupiter.api.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for sub-resource locator functionality.
 * Tests methods with @Path but no HTTP method that return sub-resource instances.
 */
@ServerTest
class SubResourceLocatorIntegrationTest {

    private final WebClient client;

    SubResourceLocatorIntegrationTest(WebClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        new TestSubResourceLocatorResource$$JaxRsRouting().register(routing);
    }

    // ========== Parent Resource Tests ==========

    @Test
    @DisplayName("Parent resource - list all")
    void testListParents() {
        String response = client.get("/parents")
                .requestEntity(String.class);

        assertThat(response, is("all-parents"));
    }

    @Test
    @DisplayName("Parent resource - get by ID")
    void testGetParent() {
        String response = client.get("/parents/42")
                .requestEntity(String.class);

        assertThat(response, is("parent:42"));
    }

    @Test
    @DisplayName("Parent resource - count")
    void testCountParents() {
        String response = client.get("/parents/count")
                .requestEntity(String.class);

        assertThat(response, is("count:100"));
    }

    // ========== Sub-Resource GET Tests ==========

    @Test
    @DisplayName("Sub-resource - GET all items")
    void testSubResourceGetAll() {
        String response = client.get("/parents/123/items")
                .requestEntity(String.class);

        assertThat(response, is("items-for:123"));
    }

    @Test
    @DisplayName("Sub-resource - GET specific item")
    void testSubResourceGetOne() {
        String response = client.get("/parents/123/items/456")
                .requestEntity(String.class);

        assertThat(response, is("item:123/456"));
    }

    @Test
    @DisplayName("Sub-resource - different parent IDs")
    void testSubResourceDifferentParents() {
        String response1 = client.get("/parents/1/items")
                .requestEntity(String.class);
        String response2 = client.get("/parents/999/items")
                .requestEntity(String.class);

        assertThat(response1, is("items-for:1"));
        assertThat(response2, is("items-for:999"));
    }

    // ========== Sub-Resource POST/PUT/DELETE Tests ==========

    @Test
    @DisplayName("Sub-resource - POST create item")
    void testSubResourcePost() {
        String response = client.post("/parents/123/items")
                .header(HeaderNames.CONTENT_TYPE, "text/plain")
                .submit("newitem")
                .as(String.class);

        assertThat(response, is("created:123/newitem"));
    }

    @Test
    @DisplayName("Sub-resource - PUT update item")
    void testSubResourcePut() {
        String response = client.put("/parents/123/items/456")
                .header(HeaderNames.CONTENT_TYPE, "text/plain")
                .submit("updated-name")
                .as(String.class);

        assertThat(response, is("updated:123/456/updated-name"));
    }

    @Test
    @DisplayName("Sub-resource - DELETE item")
    void testSubResourceDelete() {
        String response = client.delete("/parents/123/items/456")
                .requestEntity(String.class);

        assertThat(response, is("deleted:123/456"));
    }

    // ========== Sub-Resource with Query Params ==========

    @Test
    @DisplayName("Sub-resource - search with query param")
    void testSubResourceSearch() {
        String response = client.get("/parents/123/items/search")
                .queryParam("q", "test-query")
                .requestEntity(String.class);

        assertThat(response, is("search:123/test-query"));
    }

    @Test
    @DisplayName("Sub-resource - search without query param")
    void testSubResourceSearchNoQuery() {
        String response = client.get("/parents/123/items/search")
                .requestEntity(String.class);

        assertThat(response, is("search:123/all"));
    }

    // ========== Sub-Resource with Context ==========

    @Test
    @DisplayName("Sub-resource locator with multiple path params")
    void testSubResourceWithContext() {
        String response = client.get("/parents/123/ctx/mycontext/items/context")
                .requestEntity(String.class);

        assertThat(response, is("context:123/mycontext"));
    }

    @Test
    @DisplayName("Sub-resource locator with context - GET items")
    void testSubResourceWithContextGetItems() {
        String response = client.get("/parents/55/ctx/prod/items")
                .requestEntity(String.class);

        assertThat(response, is("items-for:55"));
    }

    @Test
    @DisplayName("Sub-resource locator with context - GET specific item")
    void testSubResourceWithContextGetItem() {
        String response = client.get("/parents/55/ctx/prod/items/77")
                .requestEntity(String.class);

        assertThat(response, is("item:55/77"));
    }

    // ========== Path Parameter Propagation ==========

    @Test
    @DisplayName("Parent path param passed to sub-resource")
    void testPathParamPropagation() {
        // The parent ID should be correctly passed to the sub-resource
        String response = client.get("/parents/999/items/888")
                .requestEntity(String.class);

        assertThat(response, is("item:999/888"));
    }

    @Test
    @DisplayName("Multiple sub-resource operations on same parent")
    void testMultipleOperationsOnParent() {
        // Create
        String created = client.post("/parents/100/items")
                .header(HeaderNames.CONTENT_TYPE, "text/plain")
                .submit("item1")
                .as(String.class);
        assertThat(created, is("created:100/item1"));

        // Read
        String read = client.get("/parents/100/items/1")
                .requestEntity(String.class);
        assertThat(read, is("item:100/1"));

        // Update
        String updated = client.put("/parents/100/items/1")
                .header(HeaderNames.CONTENT_TYPE, "text/plain")
                .submit("updated1")
                .as(String.class);
        assertThat(updated, is("updated:100/1/updated1"));

        // Delete
        String deleted = client.delete("/parents/100/items/1")
                .requestEntity(String.class);
        assertThat(deleted, is("deleted:100/1"));
    }
}
