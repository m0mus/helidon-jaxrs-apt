package io.helidon.examples.jaxrs.apt.test.integration;

import io.helidon.examples.jaxrs.apt.test.resources.TestCollectionParamResource$$JaxRsRouting;
import io.helidon.http.HeaderNames;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.http.HttpRouting;
import org.junit.jupiter.api.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for List/Set parameter support.
 */
@ServerTest
class CollectionParamIntegrationTest {

    private final WebClient client;

    CollectionParamIntegrationTest(WebClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        new TestCollectionParamResource$$JaxRsRouting().register(routing);
    }

    // ==================== List<String> Query Parameter Tests ====================

    @Test
    @DisplayName("List<String> query param - multiple values")
    void testQueryParamListMultiple() {
        String response = client.get("/collections/query/list")
                .queryParam("tag", "java", "kotlin", "scala")
                .requestEntity(String.class);

        assertThat(response, is("tags:java,kotlin,scala"));
    }

    @Test
    @DisplayName("List<String> query param - single value")
    void testQueryParamListSingle() {
        String response = client.get("/collections/query/list")
                .queryParam("tag", "java")
                .requestEntity(String.class);

        assertThat(response, is("tags:java"));
    }

    @Test
    @DisplayName("List<String> query param - empty (no values)")
    void testQueryParamListEmpty() {
        String response = client.get("/collections/query/empty")
                .requestEntity(String.class);

        assertThat(response, is("count:0"));
    }

    // ==================== Set<String> Query Parameter Tests ====================

    @Test
    @DisplayName("Set<String> query param - duplicates removed")
    void testQueryParamSetDuplicates() {
        String response = client.get("/collections/query/set")
                .queryParam("tag", "java", "kotlin", "java")  // duplicate java
                .requestEntity(String.class);

        assertThat(response, startsWith("tags:2:"));
        assertThat(response, containsString("java"));
        assertThat(response, containsString("kotlin"));
    }

    @Test
    @DisplayName("Set<String> query param - unique values")
    void testQueryParamSetUnique() {
        String response = client.get("/collections/query/set")
                .queryParam("tag", "alpha", "beta", "gamma")
                .requestEntity(String.class);

        assertThat(response, is("tags:3:alpha,beta,gamma"));
    }

    // ==================== List<Integer> Query Parameter Tests ====================

    @Test
    @DisplayName("List<Integer> query param - type conversion")
    void testQueryParamListInt() {
        String response = client.get("/collections/query/list/int")
                .queryParam("id", "1", "2", "3")
                .requestEntity(String.class);

        assertThat(response, is("ids:3,sum:6"));
    }

    // ==================== List<Long> Query Parameter Tests ====================

    @Test
    @DisplayName("List<Long> query param - type conversion")
    void testQueryParamListLong() {
        String response = client.get("/collections/query/list/long")
                .queryParam("id", "100", "200", "300")
                .requestEntity(String.class);

        assertThat(response, is("ids:3,sum:600"));
    }

    // ==================== List<String> Header Parameter Tests ====================

    @Test
    @DisplayName("List<String> header param - multiple values (comma-separated)")
    void testHeaderParamListMultiple() {
        // HTTP headers with same name are typically combined with commas
        String response = client.get("/collections/header/list")
                .header(HeaderNames.create("X-Tags"), "tag1,tag2")
                .requestEntity(String.class);

        assertThat(response, is("headers:tag1,tag2"));
    }

    @Test
    @DisplayName("List<String> header param - single value")
    void testHeaderParamListSingle() {
        String response = client.get("/collections/header/list")
                .header(HeaderNames.create("X-Tags"), "onlytag")
                .requestEntity(String.class);

        assertThat(response, is("headers:onlytag"));
    }

    // ==================== Set<String> Header Parameter Tests ====================

    @Test
    @DisplayName("Set<String> header param - comma separated values")
    void testHeaderParamSetCommaSeparated() {
        // Headers are typically comma-separated for multiple values
        String response = client.get("/collections/header/set")
                .header(HeaderNames.create("X-Tags"), "alpha,beta,gamma")
                .requestEntity(String.class);

        assertThat(response, startsWith("headers:"));
    }

    // ==================== Combined Parameter Tests ====================

    @Test
    @DisplayName("Combined List with path and query params")
    void testCombinedParams() {
        String response = client.get("/collections/combined/books")
                .queryParam("tag", "fiction", "bestseller")
                .queryParam("limit", "10")
                .requestEntity(String.class);

        assertThat(response, is("category:books,tags:fiction,bestseller,limit:10"));
    }
}
