package io.helidon.examples.jaxrs.apt.test.integration;

import io.helidon.examples.jaxrs.apt.test.filter.ResourceInfoLoggingFilter;
import io.helidon.examples.jaxrs.apt.test.resources.TestFilterResource$$JaxRsRouting;
import io.helidon.http.HeaderNames;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.http.HttpRouting;
import org.junit.jupiter.api.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests verifying that post-matching filters have access to ResourceInfo.
 * ResourceInfo allows filters to inspect the matched resource class and method.
 */
@ServerTest
class ResourceInfoIntegrationTest {

    private final WebClient client;

    ResourceInfoIntegrationTest(WebClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        new TestFilterResource$$JaxRsRouting().register(routing);
    }

    @BeforeEach
    void resetFilter() {
        ResourceInfoLoggingFilter.reset();
    }

    @Test
    @DisplayName("ResourceInfo is available in post-matching filter")
    void testResourceInfoAvailableInFilter() {
        // Make a request and verify ResourceInfo was injected
        var response = client.get("/filter/test").request();

        assertThat(response.status().code(), is(200));

        // ResourceInfoLoggingFilter adds headers with ResourceInfo data
        assertThat(response.headers().first(HeaderNames.create("X-Resource-Class")).orElse(null),
                is("TestFilterResource"));
        assertThat(response.headers().first(HeaderNames.create("X-Resource-Method")).orElse(null),
                is("test"));
    }

    @Test
    @DisplayName("ResourceInfo has correct method annotations")
    void testResourceInfoHasCorrectMethodAnnotations() {
        // The test method has @GET and @Path annotations
        var response = client.get("/filter/test").request();

        assertThat(response.status().code(), is(200));

        // Verify method annotations were captured
        String annotations = response.headers().first(HeaderNames.create("X-Method-Annotations")).orElse("");
        assertThat(annotations, containsString("GET"));
    }

    @Test
    @DisplayName("ResourceInfo provides correct info for different HTTP methods")
    void testResourceInfoForDifferentMethods() {
        // Test DELETE endpoint (no body needed)
        var deleteResponse = client.delete("/filter/test").request();

        assertThat(deleteResponse.status().code(), is(200));
        assertThat(deleteResponse.headers().first(HeaderNames.create("X-Resource-Method")).orElse(null),
                is("testDelete"));

        ResourceInfoLoggingFilter.reset();

        // Test POST endpoint - must specify content type
        var postResponse = client.post("/filter/test")
                .header(HeaderNames.CONTENT_TYPE, "text/plain")
                .submit("test body");

        assertThat(postResponse.status().code(), is(200));
        assertThat(postResponse.headers().first(HeaderNames.create("X-Resource-Method")).orElse(null),
                is("testPost"));

        ResourceInfoLoggingFilter.reset();

        // Test PUT endpoint - must specify content type
        var putResponse = client.put("/filter/test")
                .header(HeaderNames.CONTENT_TYPE, "text/plain")
                .submit("test body");

        assertThat(putResponse.status().code(), is(200));
        assertThat(putResponse.headers().first(HeaderNames.create("X-Resource-Method")).orElse(null),
                is("testPut"));
    }

    @Test
    @DisplayName("ResourceInfo provides correct info for name-bound endpoint")
    void testResourceInfoForNameBoundEndpoint() {
        // The audited endpoint has @AuditBinding annotation
        var response = client.get("/filter/audited").request();

        assertThat(response.status().code(), is(200));
        assertThat(response.headers().first(HeaderNames.create("X-Resource-Class")).orElse(null),
                is("TestFilterResource"));
        assertThat(response.headers().first(HeaderNames.create("X-Resource-Method")).orElse(null),
                is("audited"));

        // Should have AuditBinding annotation
        String annotations = response.headers().first(HeaderNames.create("X-Method-Annotations")).orElse("");
        assertThat(annotations, containsString("AuditBinding"));
    }

    @Test
    @DisplayName("ResourceInfo static accessors capture correct information")
    void testResourceInfoStaticAccessors() {
        // Make a request
        client.get("/filter/test").request();

        // Verify static accessors captured the info
        assertThat(ResourceInfoLoggingFilter.getLastResourceClass(), notNullValue());
        assertThat(ResourceInfoLoggingFilter.getLastResourceClass().getSimpleName(),
                is("TestFilterResource"));

        assertThat(ResourceInfoLoggingFilter.getLastResourceMethod(), notNullValue());
        assertThat(ResourceInfoLoggingFilter.getLastResourceMethod().getName(),
                is("test"));
    }

    @Test
    @DisplayName("ResourceInfo is injected via @Context annotation")
    void testResourceInfoContextInjection() {
        // The ResourceInfoLoggingFilter has @Context ResourceInfo field
        // This test verifies the injection mechanism works
        client.get("/filter/test").request();

        // If ResourceInfo wasn't injected, getLastResourceClass would be null
        assertThat(ResourceInfoLoggingFilter.getLastResourceClass(), notNullValue());
        assertThat(ResourceInfoLoggingFilter.getLastResourceMethod(), notNullValue());
    }
}
