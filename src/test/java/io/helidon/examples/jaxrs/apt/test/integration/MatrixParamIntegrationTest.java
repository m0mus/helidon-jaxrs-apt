package io.helidon.examples.jaxrs.apt.test.integration;

import io.helidon.examples.jaxrs.apt.test.resources.TestMatrixParamResource$$JaxRsRouting;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.http.HttpRouting;
import org.junit.jupiter.api.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for @MatrixParam functionality.
 * Matrix parameters are path segment parameters: /path;param=value
 */
@ServerTest
class MatrixParamIntegrationTest {

    private final WebClient client;

    MatrixParamIntegrationTest(WebClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        new TestMatrixParamResource$$JaxRsRouting().register(routing);
    }

    @Test
    @DisplayName("@MatrixParam with String - value present")
    void testMatrixParamStringPresent() {
        String response = client.get("/matrix/users;role=admin").requestEntity(String.class);

        assertThat(response, is("role:admin"));
    }

    @Test
    @DisplayName("@MatrixParam with String - value absent")
    void testMatrixParamStringAbsent() {
        String response = client.get("/matrix/users").requestEntity(String.class);

        assertThat(response, is("role:null"));
    }

    @Test
    @DisplayName("@MatrixParam with @DefaultValue - uses default")
    void testMatrixParamDefault() {
        String response = client.get("/matrix/items").requestEntity(String.class);

        assertThat(response, is("sort:id"));
    }

    @Test
    @DisplayName("@MatrixParam with @DefaultValue - value overrides default")
    void testMatrixParamDefaultOverride() {
        String response = client.get("/matrix/items;sort=name").requestEntity(String.class);

        assertThat(response, is("sort:name"));
    }

    @Test
    @DisplayName("@MatrixParam with Integer type")
    void testMatrixParamNumeric() {
        String response = client.get("/matrix/page;num=5").requestEntity(String.class);

        assertThat(response, is("page:5"));
    }

    @Test
    @DisplayName("@MatrixParam numeric - absent returns null")
    void testMatrixParamNumericAbsent() {
        String response = client.get("/matrix/page").requestEntity(String.class);

        assertThat(response, is("page:null"));
    }

    @Test
    @DisplayName("Multiple @MatrixParam - all provided")
    void testMultipleMatrixParams() {
        String response = client.get("/matrix/filter;status=active;limit=10").requestEntity(String.class);

        assertThat(response, is("status:active,limit:10"));
    }

    @Test
    @DisplayName("Multiple @MatrixParam - partial with default")
    void testMultipleMatrixParamsPartial() {
        String response = client.get("/matrix/filter;status=pending").requestEntity(String.class);

        assertThat(response, is("status:pending,limit:20"));
    }

    @Test
    @DisplayName("@MatrixParam combined with @PathParam")
    void testCombinedWithPathParam() {
        String response = client.get("/matrix/products/123;color=red").requestEntity(String.class);

        assertThat(response, is("id:123,color:red"));
    }

    @Test
    @DisplayName("@MatrixParam combined with @PathParam - matrix absent")
    void testCombinedWithPathParamNoMatrix() {
        String response = client.get("/matrix/products/456").requestEntity(String.class);

        assertThat(response, is("id:456,color:null"));
    }

    @Test
    @DisplayName("@MatrixParam combined with @QueryParam")
    void testCombinedWithQueryParam() {
        String response = client.get("/matrix/search;category=books")
                .queryParam("q", "java")
                .requestEntity(String.class);

        assertThat(response, is("category:books,query:java"));
    }

    @Test
    @DisplayName("@MatrixParam combined with @QueryParam - only query")
    void testCombinedOnlyQuery() {
        String response = client.get("/matrix/search")
                .queryParam("q", "python")
                .requestEntity(String.class);

        assertThat(response, is("category:null,query:python"));
    }
}
