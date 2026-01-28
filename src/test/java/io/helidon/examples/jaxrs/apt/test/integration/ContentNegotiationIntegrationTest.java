package io.helidon.examples.jaxrs.apt.test.integration;

import io.helidon.examples.jaxrs.apt.test.resources.TestContentNegotiationResource$$JaxRsRouting;
import io.helidon.examples.jaxrs.apt.test.util.FilterOrderTracker;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.http.HttpRouting;
import org.junit.jupiter.api.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for content negotiation.
 * Tests @Consumes and @Produces with Accept/Content-Type headers.
 */
@ServerTest
class ContentNegotiationIntegrationTest {

    private final WebClient client;

    ContentNegotiationIntegrationTest(WebClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        new TestContentNegotiationResource$$JaxRsRouting().register(routing);
    }

    // ========== @Produces Tests (Accept header) ==========

    @Test
    @DisplayName("@Produces JSON - Accept: application/json")
    void testProducesJsonAccepted() {
        String response = client.get("/content/json")
                .header(HeaderNames.ACCEPT, "application/json")
                .requestEntity(String.class);

        assertThat(response, containsString("json"));
    }

    @Test
    @DisplayName("@Produces JSON - Accept: text/html returns 406")
    void testProducesJsonNotAccepted() {
        try (HttpClientResponse response = client.get("/content/json")
                .header(HeaderNames.ACCEPT, "text/html")
                .request()) {

            assertThat(response.status(), is(Status.NOT_ACCEPTABLE_406));
        }
    }

    @Test
    @DisplayName("Response filters are skipped on negotiation failures")
    void testResponseFiltersSkippedOnNegotiationFailure() {
        FilterOrderTracker.clear();

        try (HttpClientResponse response = client.get("/content/json")
                .header(HeaderNames.ACCEPT, "text/html")
                .request()) {

            assertThat(response.status(), is(Status.NOT_ACCEPTABLE_406));
        }

        assertThat(FilterOrderTracker.getResponseFilterOrder(), is(empty()));
    }

    @Test
    @DisplayName("@Produces JSON - Accept: */* should work")
    void testProducesJsonWildcard() {
        String response = client.get("/content/json")
                .header(HeaderNames.ACCEPT, "*/*")
                .requestEntity(String.class);

        assertThat(response, containsString("json"));
    }

    @Test
    @DisplayName("@Produces JSON - No Accept header should work")
    void testProducesJsonNoAccept() {
        String response = client.get("/content/json")
                .requestEntity(String.class);

        assertThat(response, containsString("json"));
    }

    @Test
    @DisplayName("@Produces XML - Accept: application/xml")
    void testProducesXmlAccepted() {
        String response = client.get("/content/xml")
                .header(HeaderNames.ACCEPT, "application/xml")
                .requestEntity(String.class);

        assertThat(response, containsString("xml"));
    }

    @Test
    @DisplayName("@Produces XML - Accept: application/json returns 406")
    void testProducesXmlNotAccepted() {
        try (HttpClientResponse response = client.get("/content/xml")
                .header(HeaderNames.ACCEPT, "application/json")
                .request()) {

            assertThat(response.status(), is(Status.NOT_ACCEPTABLE_406));
        }
    }

    @Test
    @DisplayName("@Produces multiple - Accept first option")
    void testProducesMultipleFirstOption() {
        String response = client.get("/content/both")
                .header(HeaderNames.ACCEPT, "application/json")
                .requestEntity(String.class);

        // JSON-encoded string includes quotes
        assertThat(response, is("\"both\""));
    }

    @Test
    @DisplayName("@Produces multiple - Accept second option")
    void testProducesMultipleSecondOption() {
        String response = client.get("/content/both")
                .header(HeaderNames.ACCEPT, "application/xml")
                .requestEntity(String.class);

        // XML content type still goes through JSON serialization for String
        assertThat(response, is("\"both\""));
    }

    @Test
    @DisplayName("@Produces multiple - Accept neither returns 406")
    void testProducesMultipleNoneAccepted() {
        try (HttpClientResponse response = client.get("/content/both")
                .header(HeaderNames.ACCEPT, "text/html")
                .request()) {

            assertThat(response.status(), is(Status.NOT_ACCEPTABLE_406));
        }
    }

    @Test
    @DisplayName("Accept header with quality factors")
    void testAcceptWithQualityFactors() {
        String response = client.get("/content/json")
                .header(HeaderNames.ACCEPT, "text/html, application/json;q=0.9")
                .requestEntity(String.class);

        assertThat(response, containsString("json"));
    }

    @Test
    @DisplayName("Accept: application/* should match application/json")
    void testAcceptWildcardSubtype() {
        String response = client.get("/content/json")
                .header(HeaderNames.ACCEPT, "application/*")
                .requestEntity(String.class);

        assertThat(response, containsString("json"));
    }

    // ========== @Consumes Tests (Content-Type header) ==========

    @Test
    @DisplayName("@Consumes JSON - Content-Type: application/json")
    void testConsumesJsonCorrect() {
        String response = client.post("/content/accept-json")
                .header(HeaderNames.CONTENT_TYPE, "application/json")
                .submit("{\"test\":1}")
                .as(String.class);

        assertThat(response, startsWith("received:"));
    }

    @Test
    @DisplayName("@Consumes JSON - Content-Type: text/plain returns 415")
    void testConsumesJsonWrongContentType() {
        try (HttpClientResponse response = client.post("/content/accept-json")
                .header(HeaderNames.CONTENT_TYPE, "text/plain")
                .submit("plain text")) {

            assertThat(response.status(), is(Status.UNSUPPORTED_MEDIA_TYPE_415));
        }
    }

    @Test
    @DisplayName("@Consumes XML - Content-Type: application/xml")
    void testConsumesXmlCorrect() {
        String response = client.post("/content/accept-xml")
                .header(HeaderNames.CONTENT_TYPE, "application/xml")
                .submit("<data/>")
                .as(String.class);

        assertThat(response, startsWith("received:"));
    }

    @Test
    @DisplayName("@Consumes XML - Content-Type: application/json returns 415")
    void testConsumesXmlWrongContentType() {
        try (HttpClientResponse response = client.post("/content/accept-xml")
                .header(HeaderNames.CONTENT_TYPE, "application/json")
                .submit("{\"test\":1}")) {

            assertThat(response.status(), is(Status.UNSUPPORTED_MEDIA_TYPE_415));
        }
    }

    @Test
    @DisplayName("@Consumes multiple - JSON content type")
    void testConsumesMultipleJson() {
        String response = client.post("/content/accept-both")
                .header(HeaderNames.CONTENT_TYPE, "application/json")
                .submit("{}")
                .as(String.class);

        assertThat(response, startsWith("received:"));
    }

    @Test
    @DisplayName("@Consumes multiple - XML content type")
    void testConsumesMultipleXml() {
        String response = client.post("/content/accept-both")
                .header(HeaderNames.CONTENT_TYPE, "application/xml")
                .submit("<data/>")
                .as(String.class);

        assertThat(response, startsWith("received:"));
    }

    @Test
    @DisplayName("@Consumes multiple - text/plain returns 415")
    void testConsumesMultipleWrong() {
        try (HttpClientResponse response = client.post("/content/accept-both")
                .header(HeaderNames.CONTENT_TYPE, "text/plain")
                .submit("text")) {

            assertThat(response.status(), is(Status.UNSUPPORTED_MEDIA_TYPE_415));
        }
    }

    @Test
    @DisplayName("@Consumes form - correct content type")
    void testConsumesForm() {
        String response = client.post("/content/accept-form")
                .header(HeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .submit("data=hello")
                .as(String.class);

        assertThat(response, is("form:hello"));
    }

    @Test
    @DisplayName("Content-Type with charset parameter should work")
    void testContentTypeWithCharset() {
        String response = client.post("/content/accept-json")
                .header(HeaderNames.CONTENT_TYPE, "application/json; charset=utf-8")
                .submit("{\"test\":1}")
                .as(String.class);

        assertThat(response, startsWith("received:"));
    }

    // ========== Combined @Consumes and @Produces ==========

    @Test
    @DisplayName("PUT with both @Consumes and @Produces - all correct")
    void testBothConsumesProducesCorrect() {
        String response = client.put("/content/echo")
                .header(HeaderNames.CONTENT_TYPE, "application/json")
                .header(HeaderNames.ACCEPT, "application/json")
                .submit("{\"key\":\"value\"}")
                .as(String.class);

        assertThat(response, containsString("key"));
    }

    @Test
    @DisplayName("PUT with both - wrong Content-Type returns 415")
    void testBothWrongContentType() {
        try (HttpClientResponse response = client.put("/content/echo")
                .header(HeaderNames.CONTENT_TYPE, "text/plain")
                .header(HeaderNames.ACCEPT, "application/json")
                .submit("text")) {

            assertThat(response.status(), is(Status.UNSUPPORTED_MEDIA_TYPE_415));
        }
    }

    @Test
    @DisplayName("PUT with both - wrong Accept returns 406")
    void testBothWrongAccept() {
        try (HttpClientResponse response = client.put("/content/echo")
                .header(HeaderNames.CONTENT_TYPE, "application/json")
                .header(HeaderNames.ACCEPT, "text/plain")
                .submit("{}")) {

            assertThat(response.status(), is(Status.NOT_ACCEPTABLE_406));
        }
    }

    // ========== No restrictions ==========

    @Test
    @DisplayName("No @Produces - any Accept header should work")
    void testNoProducesAnyAccept() {
        String response = client.get("/content/any")
                .header(HeaderNames.ACCEPT, "text/html")
                .requestEntity(String.class);

        // No @Produces defaults to JSON, so String is JSON-encoded
        assertThat(response, is("\"any\""));
    }

    @Test
    @DisplayName("No @Consumes - any Content-Type should work")
    void testNoConsumesAnyContentType() {
        String response = client.post("/content/post-any")
                .header(HeaderNames.CONTENT_TYPE, "text/weird")
                .submit("data")
                .as(String.class);

        assertThat(response, is("body:data"));
    }

    // ========== Wildcard tests ==========

    @Test
    @DisplayName("@Produces text/* - Accept: text/plain should match")
    void testProducesWildcardMatchesPlain() {
        String response = client.get("/content/wildcard")
                .header(HeaderNames.ACCEPT, "text/plain")
                .requestEntity(String.class);

        assertThat(response, is("wildcard"));
    }

    @Test
    @DisplayName("@Produces text/* - Accept: text/html should match")
    void testProducesWildcardMatchesHtml() {
        String response = client.get("/content/wildcard")
                .header(HeaderNames.ACCEPT, "text/html")
                .requestEntity(String.class);

        assertThat(response, is("wildcard"));
    }

    @Test
    @DisplayName("@Produces text/* - Accept: application/json returns 406")
    void testProducesWildcardNoMatch() {
        try (HttpClientResponse response = client.get("/content/wildcard")
                .header(HeaderNames.ACCEPT, "application/json")
                .request()) {

            assertThat(response.status(), is(Status.NOT_ACCEPTABLE_406));
        }
    }
}
