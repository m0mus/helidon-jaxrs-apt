package io.helidon.examples.jaxrs.apt.test.integration;

import io.helidon.examples.jaxrs.apt.test.resources.TestExceptionResource$$JaxRsRouting;
import io.helidon.http.HeaderNames;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.http.HttpRouting;
import org.junit.jupiter.api.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for ExceptionMapper functionality.
 */
@ServerTest
class ExceptionMapperIntegrationTest {

    private final WebClient client;

    ExceptionMapperIntegrationTest(WebClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        new TestExceptionResource$$JaxRsRouting().register(routing);
    }

    @Test
    @DisplayName("CustomBusinessException mapped to 422 with custom body")
    void testCustomBusinessExceptionMapper() {
        var response = client.get("/exceptions/business").request();

        assertThat(response.status().code(), is(422));
        String body = response.as(String.class);
        assertThat(body, containsString("\"error\":\"Business rule violated\""));
        assertThat(body, containsString("\"code\":\"BIZ001\""));
        assertThat(response.headers().first(HeaderNames.create("X-Error-Code")).orElse(""), is("BIZ001"));
    }

    @Test
    @DisplayName("ValidationException mapped to 400 with validation details")
    void testValidationExceptionMapper() {
        var response = client.get("/exceptions/validation").request();

        assertThat(response.status().code(), is(400));
        String body = response.as(String.class);
        assertThat(body, containsString("\"error\":\"Invalid email format\""));
        assertThat(body, containsString("\"field\":\"email\""));
        assertThat(response.headers().first(HeaderNames.create("X-Validation-Field")).orElse(""), is("email"));
    }

    @Test
    @DisplayName("NotFoundException uses default handling (404)")
    void testNotFoundExceptionDefaultHandling() {
        var response = client.get("/exceptions/notfound").request();

        assertThat(response.status().code(), is(404));
        String body = response.as(String.class);
        assertThat(body, containsString("Resource not found"));
    }

    @Test
    @DisplayName("RuntimeException uses default handling (500)")
    void testRuntimeExceptionDefaultHandling() {
        var response = client.get("/exceptions/runtime").request();

        assertThat(response.status().code(), is(500));
        String body = response.as(String.class);
        assertThat(body, containsString("Unexpected error occurred"));
    }

    @Test
    @DisplayName("Normal endpoint works without exception")
    void testNormalEndpoint() {
        String response = client.get("/exceptions/ok").requestEntity(String.class);

        assertThat(response, is("OK"));
    }
}
