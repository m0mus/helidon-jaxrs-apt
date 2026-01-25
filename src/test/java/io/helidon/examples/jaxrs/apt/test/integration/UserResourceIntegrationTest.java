package io.helidon.examples.jaxrs.apt.test.integration;

import io.helidon.examples.jaxrs.apt.UserResource$$JaxRsRouting;
import io.helidon.http.HeaderNames;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.http.HttpRouting;
import org.junit.jupiter.api.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for UserResource CRUD operations.
 */
@ServerTest
class UserResourceIntegrationTest {

    private final WebClient client;

    UserResourceIntegrationTest(WebClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        new UserResource$$JaxRsRouting().register(routing);
    }

    @Test
    @DisplayName("GET /users - List all users")
    void testListUsers() {
        String response = client.get("/users").requestEntity(String.class);

        assertThat(response, containsString("Alice"));
        assertThat(response, containsString("Bob"));
    }

    @Test
    @DisplayName("GET /users?name=Alice - Filter users by name")
    void testListUsersWithNameFilter() {
        String response = client.get("/users")
                .queryParam("name", "Alice")
                .requestEntity(String.class);

        assertThat(response, containsString("Alice"));
        assertThat(response, not(containsString("Bob")));
    }

    @Test
    @DisplayName("GET /users/{id} - Get existing user")
    void testGetExistingUser() {
        String response = client.get("/users/1").requestEntity(String.class);

        assertThat(response, containsString("Alice"));
        assertThat(response, containsString("alice@example.com"));
    }

    @Test
    @DisplayName("GET /users/{id} - Get non-existing user returns 404")
    void testGetNonExistingUser() {
        var response = client.get("/users/999").request();

        assertThat(response.status().code(), is(404));
    }

    @Test
    @DisplayName("POST /users - Create new user")
    void testCreateUser() {
        String json = "{\"name\":\"Charlie\",\"email\":\"charlie@example.com\"}";

        var response = client.post("/users")
                .header(HeaderNames.CONTENT_TYPE, "application/json")
                .submit(json);

        assertThat(response.status().code(), is(200));
        String body = response.as(String.class);
        assertThat(body, containsString("Charlie"));
        assertThat(body, containsString("charlie@example.com"));
        assertThat(body, containsString("\"id\":"));
    }

    @Test
    @DisplayName("PUT /users/{id} - Update existing user")
    void testUpdateExistingUser() {
        // First create a user
        String createJson = "{\"name\":\"TestUpdate\",\"email\":\"testupdate@example.com\"}";
        var createResponse = client.post("/users")
                .header(HeaderNames.CONTENT_TYPE, "application/json")
                .submit(createJson);

        String createBody = createResponse.as(String.class);

        // Extract the ID from the response
        int idStart = createBody.indexOf("\"id\":") + 5;
        int idEnd = createBody.indexOf(",", idStart);
        String id = createBody.substring(idStart, idEnd);

        // Update the user
        String updateJson = "{\"name\":\"UpdatedName\",\"email\":\"updated@example.com\"}";
        var updateResponse = client.put("/users/" + id)
                .header(HeaderNames.CONTENT_TYPE, "application/json")
                .submit(updateJson);

        String updateBody = updateResponse.as(String.class);
        assertThat(updateBody, containsString("UpdatedName"));
        assertThat(updateBody, containsString("updated@example.com"));
    }

    @Test
    @DisplayName("PUT /users/{id} - Update non-existing user returns 404")
    void testUpdateNonExistingUser() {
        String json = "{\"name\":\"NoOne\",\"email\":\"noone@example.com\"}";

        var response = client.put("/users/999")
                .header(HeaderNames.CONTENT_TYPE, "application/json")
                .submit(json);

        assertThat(response.status().code(), is(404));
    }

    @Test
    @DisplayName("DELETE /users/{id} - Delete existing user")
    void testDeleteExistingUser() {
        // First create a user to delete
        String createJson = "{\"name\":\"ToDelete\",\"email\":\"delete@example.com\"}";
        var createResponse = client.post("/users")
                .header(HeaderNames.CONTENT_TYPE, "application/json")
                .submit(createJson);

        String createBody = createResponse.as(String.class);

        // Extract the ID
        int idStart = createBody.indexOf("\"id\":") + 5;
        int idEnd = createBody.indexOf(",", idStart);
        String id = createBody.substring(idStart, idEnd);

        // Delete the user
        var deleteResponse = client.delete("/users/" + id).request();

        assertThat(deleteResponse.status().code(), is(204));

        // Verify it's deleted
        var getResponse = client.get("/users/" + id).request();

        assertThat(getResponse.status().code(), is(404));
    }

    @Test
    @DisplayName("DELETE /users/{id} - Delete non-existing user returns 404")
    void testDeleteNonExistingUser() {
        var response = client.delete("/users/999").request();

        assertThat(response.status().code(), is(404));
    }

    @Test
    @DisplayName("GET /users/count - Count users returns plain text")
    void testCountUsers() {
        var response = client.get("/users/count").request();

        assertThat(response.status().code(), is(200));

        String body = response.as(String.class);
        assertThat(body, startsWith("Total users:"));
    }
}
