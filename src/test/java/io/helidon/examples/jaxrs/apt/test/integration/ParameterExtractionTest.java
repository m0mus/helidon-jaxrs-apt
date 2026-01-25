package io.helidon.examples.jaxrs.apt.test.integration;

import io.helidon.examples.jaxrs.apt.test.resources.TestParameterResource$$JaxRsRouting;
import io.helidon.http.HeaderNames;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.http.HttpRouting;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for parameter extraction from JAX-RS annotations.
 */
@ServerTest
class ParameterExtractionTest {

    private final WebClient client;

    ParameterExtractionTest(WebClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        new TestParameterResource$$JaxRsRouting().register(routing);
    }

    // PathParam Tests

    @Test
    @DisplayName("@PathParam with String type")
    void testPathParamString() {
        String response = client.get("/params/path/hello").requestEntity(String.class);

        assertThat(response, is("path:hello"));
    }

    @ParameterizedTest
    @CsvSource({
            "test-value, path:test-value",
            "123, path:123",
            "hello-world, path:hello-world"
    })
    @DisplayName("@PathParam with various String values")
    void testPathParamStringVariations(String value, String expected) {
        String response = client.get("/params/path/" + value).requestEntity(String.class);

        assertThat(response, is(expected));
    }

    @Test
    @DisplayName("@PathParam with Long type")
    void testPathParamNumeric() {
        String response = client.get("/params/path/numeric/42").requestEntity(String.class);

        assertThat(response, is("id:42"));
    }

    // QueryParam Tests

    @Test
    @DisplayName("@QueryParam - value present")
    void testQueryParamPresent() {
        String response = client.get("/params/query")
                .queryParam("q", "searchterm")
                .requestEntity(String.class);

        assertThat(response, is("query:searchterm"));
    }

    @Test
    @DisplayName("@QueryParam - value absent")
    void testQueryParamAbsent() {
        String response = client.get("/params/query").requestEntity(String.class);

        assertThat(response, is("query:null"));
    }

    @Test
    @DisplayName("@QueryParam with @DefaultValue - uses default")
    void testQueryParamDefaultValue() {
        String response = client.get("/params/query/default").requestEntity(String.class);

        assertThat(response, is("query:defaultValue"));
    }

    @Test
    @DisplayName("@QueryParam with @DefaultValue - value provided overrides default")
    void testQueryParamDefaultValueOverride() {
        String response = client.get("/params/query/default")
                .queryParam("q", "custom")
                .requestEntity(String.class);

        assertThat(response, is("query:custom"));
    }

    @Test
    @DisplayName("Multiple @QueryParam")
    void testMultipleQueryParams() {
        String response = client.get("/params/query/multiple")
                .queryParam("a", "valueA")
                .queryParam("b", "valueB")
                .requestEntity(String.class);

        assertThat(response, is("a:valueA,b:valueB"));
    }

    @Test
    @DisplayName("Multiple @QueryParam - partial values")
    void testMultipleQueryParamsPartial() {
        String response = client.get("/params/query/multiple")
                .queryParam("a", "valueA")
                .requestEntity(String.class);

        assertThat(response, is("a:valueA,b:defaultB"));
    }

    // HeaderParam Tests

    @Test
    @DisplayName("@HeaderParam - value present")
    void testHeaderParamPresent() {
        String response = client.get("/params/header")
                .header(HeaderNames.create("X-Custom-Header"), "headervalue")
                .requestEntity(String.class);

        assertThat(response, is("header:headervalue"));
    }

    @Test
    @DisplayName("@HeaderParam - value absent")
    void testHeaderParamAbsent() {
        String response = client.get("/params/header").requestEntity(String.class);

        assertThat(response, is("header:null"));
    }

    @Test
    @DisplayName("@HeaderParam with @DefaultValue")
    void testHeaderParamDefaultValue() {
        String response = client.get("/params/header/default").requestEntity(String.class);

        assertThat(response, is("header:defaultHeader"));
    }

    // CookieParam Tests

    @Test
    @DisplayName("@CookieParam - value present")
    void testCookieParamPresent() {
        String response = client.get("/params/cookie")
                .header(HeaderNames.COOKIE, "session=abc123")
                .requestEntity(String.class);

        assertThat(response, is("cookie:abc123"));
    }

    @Test
    @DisplayName("@CookieParam - value absent")
    void testCookieParamAbsent() {
        String response = client.get("/params/cookie").requestEntity(String.class);

        assertThat(response, is("cookie:null"));
    }

    @Test
    @DisplayName("@CookieParam with @DefaultValue")
    void testCookieParamDefaultValue() {
        String response = client.get("/params/cookie/default").requestEntity(String.class);

        assertThat(response, is("cookie:defaultSession"));
    }

    // FormParam Tests

    @Test
    @DisplayName("@FormParam - single field")
    void testFormParamSingle() {
        var response = client.post("/params/form")
                .header(HeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .submit("field=formvalue");

        String body = response.as(String.class);
        assertThat(body, is("form:formvalue"));
    }

    @Test
    @DisplayName("@FormParam - multiple fields")
    void testFormParamMultiple() {
        var response = client.post("/params/form/multiple")
                .header(HeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .submit("name=John&email=john@example.com");

        String body = response.as(String.class);
        assertThat(body, is("name:John,email:john@example.com"));
    }

    // Context Tests

    @Test
    @DisplayName("@Context UriInfo")
    void testContextUriInfo() {
        String response = client.get("/params/context/uriinfo").requestEntity(String.class);

        assertThat(response, containsString("path:"));
        assertThat(response, containsString("context/uriinfo"));
    }

    @Test
    @DisplayName("@Context HttpHeaders")
    void testContextHeaders() {
        String response = client.get("/params/context/headers").requestEntity(String.class);

        assertThat(response, startsWith("host:"));
    }

    // Combined Parameter Tests

    @Test
    @DisplayName("Combined parameters - all provided")
    void testCombinedParamsAllProvided() {
        String response = client.get("/params/combined/123")
                .queryParam("filter", "active")
                .header(HeaderNames.create("X-Token"), "secret")
                .requestEntity(String.class);

        assertThat(response, is("id:123,filter:active,token:secret"));
    }

    @Test
    @DisplayName("Combined parameters - partial")
    void testCombinedParamsPartial() {
        String response = client.get("/params/combined/456").requestEntity(String.class);

        assertThat(response, is("id:456,filter:null,token:null"));
    }

    // ==================== Response Return Type Tests ====================

    @Test
    @DisplayName("Simple Response - basic case")
    void testResponseSimple() {
        var response = client.get("/params/response/simple").request();

        assertThat(response.status().code(), is(200));
        assertThat(response.as(String.class), is("hello"));
    }

    @Test
    @DisplayName("Response with custom status code 201")
    void testResponseWithStatus() {
        var response = client.get("/params/response/status").request();

        assertThat(response.status().code(), is(201));
        assertThat(response.as(String.class), is("created"));
    }

    @Test
    @DisplayName("Response with JSON entity")
    void testResponseWithJson() {
        var response = client.get("/params/response/json").request();

        assertThat(response.status().code(), is(200));
        String body = response.as(String.class);
        assertThat(body, containsString("\"name\":\"test\""));
        assertThat(body, containsString("\"value\":42"));
    }

    @Test
    @DisplayName("Response with custom headers")
    void testResponseWithHeaders() {
        var response = client.get("/params/response/headers").request();

        assertThat(response.status().code(), is(200));
        assertThat(response.headers().first(HeaderNames.create("X-Custom-Response")).orElse(""), is("custom-value"));
        assertThat(response.headers().first(HeaderNames.create("X-Request-Id")).orElse(""), is("12345"));
    }

    @Test
    @DisplayName("Response with no content (204)")
    void testResponseNoContent() {
        var response = client.delete("/params/response/nocontent").request();

        assertThat(response.status().code(), is(204));
    }

    @Test
    @DisplayName("Response with error status 400")
    void testResponseError() {
        var response = client.get("/params/response/error").request();

        assertThat(response.status().code(), is(400));
        assertThat(response.as(String.class), is("Bad request data"));
    }
}
