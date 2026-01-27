package io.helidon.examples.jaxrs.apt.test.integration;

import io.helidon.examples.jaxrs.apt.test.resources.TestFilterResource$$JaxRsRouting;
import io.helidon.examples.jaxrs.apt.test.util.FilterOrderTracker;
import io.helidon.http.HeaderNames;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.http.HttpRouting;
import org.junit.jupiter.api.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for pre-matching filter functionality.
 * Tests that pre-matching filters run BEFORE routing and can:
 * - Modify the request URI (affecting which route is matched)
 * - Modify the HTTP method
 * - Abort the request before routing
 */
@ServerTest
class PreMatchingFilterIntegrationTest {

    private final WebClient client;

    PreMatchingFilterIntegrationTest(WebClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        new TestFilterResource$$JaxRsRouting().register(routing);
    }

    // ========== URI Rewriting Tests ==========

    @Test
    @DisplayName("Pre-matching filter rewrites URI - legacy path to new path")
    void testUriRewritingLegacyPath() {
        // Request /legacy/resource should be rewritten to /filter/test
        var response = client.get("/legacy/resource")
                .request();

        // Should get response from /filter/test endpoint
        assertThat(response.status().code(), is(200));
        String body = response.as(String.class);
        assertThat(body, is("filter-test"));
    }

    @Test
    @DisplayName("Pre-matching filter rewrites URI - v1 to new path with query param")
    void testUriRewritingWithQueryParam() {
        // Request /v1/items should be rewritten to /filter/test?rewritten=true
        var response = client.get("/v1/items")
                .request();

        // Should get response from /filter/test endpoint
        assertThat(response.status().code(), is(200));
    }

    @Test
    @DisplayName("Normal request without rewriting works")
    void testNormalRequestNotRewritten() {
        // Direct request to /filter/test should work normally
        var response = client.get("/filter/test")
                .request();

        assertThat(response.status().code(), is(200));
        String body = response.as(String.class);
        assertThat(body, is("filter-test"));
    }

    // ========== Method Override Tests ==========

    @Test
    @DisplayName("Pre-matching filter overrides POST to PUT")
    void testMethodOverridePostToPut() {
        // Send POST with X-HTTP-Method-Override: PUT
        // Should be routed to PUT handler
        var response = client.post("/filter/test")
                .header(HeaderNames.create("X-HTTP-Method-Override"), "PUT")
                .header(HeaderNames.CONTENT_TYPE, "text/plain")
                .submit("updated-value");

        assertThat(response.status().code(), is(200));
        String body = response.as(String.class);
        assertThat(body, containsString("updated")); // PUT handler response
    }

    @Test
    @DisplayName("Pre-matching filter overrides POST to DELETE")
    void testMethodOverridePostToDelete() {
        // Send POST with X-HTTP-Method-Override: DELETE
        // Should be routed to DELETE handler
        var response = client.post("/filter/test")
                .header(HeaderNames.create("X-HTTP-Method-Override"), "DELETE")
                .request();

        assertThat(response.status().code(), is(200));
        String body = response.as(String.class);
        assertThat(body, containsString("deleted")); // DELETE handler response
    }

    @Test
    @DisplayName("POST without override header routes normally")
    void testPostWithoutOverride() {
        // Normal POST without override header should go to POST handler
        var response = client.post("/filter/test")
                .header(HeaderNames.CONTENT_TYPE, "text/plain")
                .submit("new-value");

        assertThat(response.status().code(), is(200));
        String body = response.as(String.class);
        assertThat(body, containsString("created")); // POST handler response
    }

    // ========== Pre-Matching Abort Tests ==========

    @Test
    @DisplayName("Pre-matching filter aborts request before routing")
    void testPreMatchingAbort() {
        // Request with X-Abort-Request header should be aborted
        var response = client.get("/filter/test")
                .header(HeaderNames.create("X-Abort-Request"), "security-check-failed")
                .request();

        assertThat(response.status().code(), is(403)); // Forbidden
        String body = response.as(String.class);
        assertThat(body, containsString("aborted"));
        assertThat(body, containsString("security-check-failed"));
    }

    @Test
    @DisplayName("Pre-matching abort prevents reaching endpoint")
    void testAbortPreventsEndpointExecution() {
        // Request to a valid endpoint with abort header
        var response = client.get("/filter/audited")
                .header(HeaderNames.create("X-Abort-Request"), "blocked")
                .request();

        // Should be aborted, not reach the endpoint
        assertThat(response.status().code(), is(403));
    }

    @Test
    @DisplayName("Request without abort header proceeds normally")
    void testRequestWithoutAbortHeader() {
        // Normal request without abort header should succeed
        var response = client.get("/filter/test")
                .request();

        assertThat(response.status().code(), is(200));
    }

    // ========== Combined Tests ==========

    @Test
    @DisplayName("Pre-matching filters run in priority order")
    void testFilterPriorityOrder() {
        // Abort filter has priority 10, runs first
        // If abort header is present, URI rewriting (priority 100) should not matter
        var response = client.get("/legacy/resource")
                .header(HeaderNames.create("X-Abort-Request"), "early-abort")
                .request();

        assertThat(response.status().code(), is(403));
    }

    @Test
    @DisplayName("URI rewriting combined with method override")
    void testUriRewritingWithMethodOverride() {
        // Request to /legacy/resource with method override
        // Should be rewritten to /filter/test and then method override applied
        var response = client.post("/legacy/resource")
                .header(HeaderNames.create("X-HTTP-Method-Override"), "DELETE")
                .request();

        assertThat(response.status().code(), is(200));
        String body = response.as(String.class);
        assertThat(body, containsString("deleted"));
    }

    // ========== @Context Injection Tests ==========

    @Test
    @DisplayName("Pre-matching filter can access @Context UriInfo with correct path")
    void testPreMatchingFilterUriInfoAccess() {
        FilterOrderTracker.clear();

        var response = client.get("/filter/test")
                .header(HeaderNames.create("X-Test-PreMatching-Context"), "true")
                .request();

        assertThat(response.status().code(), is(200));

        var requestFilters = FilterOrderTracker.getRequestFilterOrder();
        // Find the detailed context filter entry
        String contextEntry = requestFilters.stream()
                .filter(s -> s.startsWith("PreMatchingContextFilter:") && s.contains("path="))
                .findFirst()
                .orElse("");

        assertThat("UriInfo.getPath() should return the request path",
                contextEntry, containsString("path=/filter/test"));
    }

    @Test
    @DisplayName("Pre-matching filter can access @Context HttpHeaders with custom header")
    void testPreMatchingFilterHttpHeadersAccess() {
        FilterOrderTracker.clear();

        var response = client.get("/filter/test")
                .header(HeaderNames.create("X-Test-PreMatching-Context"), "true")
                .header(HeaderNames.create("X-Custom-Test-Header"), "test-value-123")
                .request();

        assertThat(response.status().code(), is(200));

        var requestFilters = FilterOrderTracker.getRequestFilterOrder();
        String contextEntry = requestFilters.stream()
                .filter(s -> s.startsWith("PreMatchingContextFilter:") && s.contains("customHeader="))
                .findFirst()
                .orElse("");

        assertThat("HttpHeaders should read custom header value",
                contextEntry, containsString("customHeader=test-value-123"));
    }

    @Test
    @DisplayName("Pre-matching filter can access @Context SecurityContext")
    void testPreMatchingFilterSecurityContextAccess() {
        FilterOrderTracker.clear();

        var response = client.get("/filter/test")
                .header(HeaderNames.create("X-Test-PreMatching-Context"), "true")
                .request();

        assertThat(response.status().code(), is(200));

        var requestFilters = FilterOrderTracker.getRequestFilterOrder();
        String contextEntry = requestFilters.stream()
                .filter(s -> s.startsWith("PreMatchingContextFilter:") && s.contains("secure="))
                .findFirst()
                .orElse("");

        // HTTP test client is not secure
        assertThat("SecurityContext.isSecure() should work",
                contextEntry, containsString("secure=false"));
    }

    @Test
    @DisplayName("Pre-matching filter CANNOT access @Context ResourceInfo (not yet available)")
    void testPreMatchingFilterResourceInfoNotAvailable() {
        FilterOrderTracker.clear();

        var response = client.get("/filter/test")
                .header(HeaderNames.create("X-Test-PreMatching-Context"), "true")
                .request();

        assertThat(response.status().code(), is(200));

        var requestFilters = FilterOrderTracker.getRequestFilterOrder();
        String contextEntry = requestFilters.stream()
                .filter(s -> s.startsWith("PreMatchingContextFilter:") && s.contains("resourceInfo="))
                .findFirst()
                .orElse("");

        // ResourceInfo should not be available in pre-matching filters
        assertThat("ResourceInfo should NOT be available in pre-matching filter",
                contextEntry, containsString("resourceInfo=NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("Pre-matching filter context works with different request paths")
    void testPreMatchingFilterContextWithDifferentPaths() {
        FilterOrderTracker.clear();

        // Test with /filter/audited path
        var response = client.get("/filter/audited")
                .header(HeaderNames.create("X-Test-PreMatching-Context"), "true")
                .request();

        assertThat(response.status().code(), is(200));

        var requestFilters = FilterOrderTracker.getRequestFilterOrder();
        String contextEntry = requestFilters.stream()
                .filter(s -> s.startsWith("PreMatchingContextFilter:") && s.contains("path="))
                .findFirst()
                .orElse("");

        assertThat("UriInfo should reflect the actual request path",
                contextEntry, containsString("path=/filter/audited"));
    }

    @Test
    @DisplayName("Pre-matching filter context injection has no errors")
    void testPreMatchingFilterNoContextErrors() {
        FilterOrderTracker.clear();

        var response = client.get("/filter/test")
                .header(HeaderNames.create("X-Test-PreMatching-Context"), "true")
                .request();

        assertThat(response.status().code(), is(200));

        var requestFilters = FilterOrderTracker.getRequestFilterOrder();

        // No ERROR entries should exist
        assertThat("No context access errors should occur", requestFilters,
                not(hasItem(containsString("ERROR"))));

        // No NULL_PROXY entries (proxies should be injected)
        assertThat("Context proxies should be injected", requestFilters,
                not(hasItem(containsString("NULL_PROXY"))));
    }
}
