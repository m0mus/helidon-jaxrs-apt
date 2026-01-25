package io.helidon.examples.jaxrs.apt.test.integration;

import io.helidon.examples.jaxrs.apt.test.resources.TestBeanParamResource$$JaxRsRouting;
import io.helidon.http.HeaderNames;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.http.HttpRouting;
import org.junit.jupiter.api.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for @BeanParam functionality.
 */
@ServerTest
class BeanParamIntegrationTest {

    private final WebClient client;

    BeanParamIntegrationTest(WebClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        new TestBeanParamResource$$JaxRsRouting().register(routing);
    }

    // ==================== SearchParams Tests (@QueryParam + @HeaderParam) ====================

    @Test
    @DisplayName("@BeanParam with all query params provided")
    void testBeanParamAllQueryParams() {
        String response = client.get("/beanparam/search")
                .queryParam("q", "test")
                .queryParam("page", "2")
                .queryParam("size", "20")
                .header(HeaderNames.create("X-Sort-Order"), "desc")
                .requestEntity(String.class);

        assertThat(response, is("q=test,page=2,size=20,sort=desc"));
    }

    @Test
    @DisplayName("@BeanParam with default values")
    void testBeanParamDefaultValues() {
        String response = client.get("/beanparam/search")
                .queryParam("q", "search")
                .requestEntity(String.class);

        assertThat(response, is("q=search,page=1,size=10,sort=asc"));
    }

    @Test
    @DisplayName("@BeanParam with partial params")
    void testBeanParamPartialParams() {
        String response = client.get("/beanparam/search")
                .queryParam("q", "hello")
                .queryParam("page", "5")
                .requestEntity(String.class);

        assertThat(response, is("q=hello,page=5,size=10,sort=asc"));
    }

    @Test
    @DisplayName("@BeanParam with header param override")
    void testBeanParamHeaderOverride() {
        String response = client.get("/beanparam/search")
                .queryParam("q", "data")
                .header(HeaderNames.create("X-Sort-Order"), "desc")
                .requestEntity(String.class);

        assertThat(response, is("q=data,page=1,size=10,sort=desc"));
    }

    // ==================== FormDataParams Tests (@FormParam) ====================

    @Test
    @DisplayName("@BeanParam with form params - all provided")
    void testBeanParamFormAllParams() {
        var response = client.post("/beanparam/form")
                .header(HeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .submit("username=john&email=john@example.com&age=30");

        String body = response.as(String.class);
        assertThat(body, is("username=john,email=john@example.com,age=30"));
    }

    @Test
    @DisplayName("@BeanParam with form params - partial with defaults")
    void testBeanParamFormPartialParams() {
        var response = client.post("/beanparam/form")
                .header(HeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .submit("username=jane&email=jane@example.com");

        String body = response.as(String.class);
        assertThat(body, is("username=jane,email=jane@example.com,age=0"));
    }

    // ==================== Combined Tests (@BeanParam + @PathParam) ====================

    @Test
    @DisplayName("@BeanParam combined with @PathParam")
    void testBeanParamCombinedWithPathParam() {
        String response = client.get("/beanparam/combined/123")
                .queryParam("q", "search")
                .queryParam("page", "3")
                .requestEntity(String.class);

        assertThat(response, is("id=123,q=search,page=3,size=10,sort=asc"));
    }

    @Test
    @DisplayName("@BeanParam combined with @PathParam - all params")
    void testBeanParamCombinedAllParams() {
        String response = client.get("/beanparam/combined/456")
                .queryParam("q", "fulltest")
                .queryParam("page", "10")
                .queryParam("size", "50")
                .header(HeaderNames.create("X-Sort-Order"), "desc")
                .requestEntity(String.class);

        assertThat(response, is("id=456,q=fulltest,page=10,size=50,sort=desc"));
    }
}
