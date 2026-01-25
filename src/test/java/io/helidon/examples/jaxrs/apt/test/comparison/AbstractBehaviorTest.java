package io.helidon.examples.jaxrs.apt.test.comparison;

import io.helidon.http.HeaderNames;
import io.helidon.webclient.api.WebClient;
import org.junit.jupiter.api.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Abstract base class for behavioral comparison tests.
 * Contains all test methods that should produce identical results
 * whether running against APT-generated code or Jersey runtime.
 *
 * <p>Subclasses must implement {@link #getClient()} to provide
 * the WebClient configured for their specific implementation.
 */
public abstract class AbstractBehaviorTest {

    /**
     * Get the WebClient configured for the specific implementation.
     *
     * @return the web client
     */
    protected abstract WebClient getClient();

    // ==================== CRUD Operations ====================

    @Test
    @DisplayName("GET /users - List all users returns 200")
    void testListUsersReturns200() {
        var response = getClient().get("/users").request();
        assertThat(response.status().code(), is(200));
    }

    @Test
    @DisplayName("GET /users - Response contains JSON")
    void testListUsersReturnsJson() {
        var response = getClient().get("/users").request();
        String contentType = response.headers().first(HeaderNames.CONTENT_TYPE).orElse("");
        assertThat(contentType, containsString("application/json"));
    }

    @Test
    @DisplayName("GET /users/{id} - Existing user returns 200")
    void testGetExistingUserReturns200() {
        var response = getClient().get("/users/1").request();
        assertThat(response.status().code(), is(200));
    }

    @Test
    @DisplayName("GET /users/{id} - Non-existing user returns 404")
    void testGetNonExistingUserReturns404() {
        var response = getClient().get("/users/99999").request();
        assertThat(response.status().code(), is(404));
    }

    @Test
    @DisplayName("POST /users - Create returns user with ID")
    void testCreateUserReturnsId() {
        String json = "{\"name\":\"Test\",\"email\":\"test@example.com\"}";
        var response = getClient().post("/users")
                .header(HeaderNames.CONTENT_TYPE, "application/json")
                .submit(json);

        assertThat(response.status().code(), is(200));
        String body = response.as(String.class);
        assertThat(body, containsString("\"id\":"));
        assertThat(body, containsString("Test"));
    }

    @Test
    @DisplayName("PUT /users/{id} - Update non-existing returns 404")
    void testUpdateNonExistingReturns404() {
        String json = "{\"name\":\"Updated\",\"email\":\"updated@example.com\"}";
        var response = getClient().put("/users/99999")
                .header(HeaderNames.CONTENT_TYPE, "application/json")
                .submit(json);

        assertThat(response.status().code(), is(404));
    }

    @Test
    @DisplayName("DELETE /users/{id} - Delete non-existing returns 404")
    void testDeleteNonExistingReturns404() {
        var response = getClient().delete("/users/99999").request();
        assertThat(response.status().code(), is(404));
    }

    // ==================== Parameter Extraction ====================

    @Test
    @DisplayName("@QueryParam - Filter users by name")
    void testQueryParamFilter() {
        var response = getClient().get("/users")
                .queryParam("name", "Alice")
                .request();

        String body = response.as(String.class);
        assertThat(body, containsString("Alice"));
    }

    @Test
    @DisplayName("@PathParam - Numeric path parameter")
    void testPathParamNumeric() {
        var response = getClient().get("/params/path/numeric/42").request();
        String body = response.as(String.class);
        assertThat(body, is("id:42"));
    }

    @Test
    @DisplayName("@QueryParam with @DefaultValue")
    void testQueryParamDefaultValue() {
        var response = getClient().get("/params/query/default").request();
        String body = response.as(String.class);
        assertThat(body, is("query:defaultValue"));
    }

    @Test
    @DisplayName("@HeaderParam extraction")
    void testHeaderParam() {
        var response = getClient().get("/params/header")
                .header(HeaderNames.create("X-Custom-Header"), "myvalue")
                .request();

        String body = response.as(String.class);
        assertThat(body, is("header:myvalue"));
    }

    @Test
    @DisplayName("@FormParam extraction")
    void testFormParam() {
        var response = getClient().post("/params/form")
                .header(HeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .submit("field=formdata");

        String body = response.as(String.class);
        assertThat(body, is("form:formdata"));
    }

    // ==================== Content Negotiation ====================

    @Test
    @DisplayName("Plain text response with correct Content-Type")
    void testPlainTextContentType() {
        var response = getClient().get("/users/count").request();

        String contentType = response.headers().first(HeaderNames.CONTENT_TYPE).orElse("");
        assertThat(contentType, containsString("text/plain"));
    }

    @Test
    @DisplayName("JSON response with correct Content-Type")
    void testJsonContentType() {
        var response = getClient().get("/users").request();

        String contentType = response.headers().first(HeaderNames.CONTENT_TYPE).orElse("");
        assertThat(contentType, containsString("application/json"));
    }

    // ==================== Exception Handling ====================

    @Test
    @DisplayName("NotFoundException returns 404 status")
    void testNotFoundExceptionStatus() {
        var response = getClient().get("/users/99999").request();
        assertThat(response.status().code(), is(404));
    }

    @Test
    @DisplayName("NotFoundException includes error message")
    void testNotFoundExceptionMessage() {
        var response = getClient().get("/users/99999").request();
        String body = response.as(String.class);
        assertThat(body.toLowerCase(), containsString("not found"));
    }

    // ==================== Void Response ====================

    @Test
    @DisplayName("Void method returns 204 No Content")
    void testVoidMethodReturns204() {
        // First create a user to delete
        String json = "{\"name\":\"ToDelete\",\"email\":\"delete@example.com\"}";
        var createResponse = getClient().post("/users")
                .header(HeaderNames.CONTENT_TYPE, "application/json")
                .submit(json);

        String createBody = createResponse.as(String.class);

        // Extract ID
        int idStart = createBody.indexOf("\"id\":") + 5;
        int idEnd = createBody.indexOf(",", idStart);
        String id = createBody.substring(idStart, idEnd);

        // Delete should return 204
        var deleteResponse = getClient().delete("/users/" + id).request();
        assertThat(deleteResponse.status().code(), is(204));
    }
}
