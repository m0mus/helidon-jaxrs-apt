package io.helidon.examples.jaxrs.apt.test.integration;

import io.helidon.examples.jaxrs.apt.test.resources.TestSecurityContextResource$$JaxRsRouting;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.http.HttpRouting;
import org.junit.jupiter.api.*;

import java.util.Base64;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for @Context SecurityContext functionality.
 */
@ServerTest
class SecurityContextIntegrationTest {

    private final WebClient client;

    SecurityContextIntegrationTest(WebClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        new TestSecurityContextResource$$JaxRsRouting().register(routing);
    }

    // ========== Principal Tests ==========

    @Test
    @DisplayName("SecurityContext - no auth returns null principal")
    void testNoPrincipal() {
        String response = client.get("/security/principal")
                .requestEntity(String.class);

        assertThat(response, is("principal:null"));
    }

    @Test
    @DisplayName("SecurityContext - Basic auth extracts username")
    void testBasicAuthPrincipal() {
        String credentials = Base64.getEncoder().encodeToString("alice:secret".getBytes());

        String response = client.get("/security/principal")
                .header(HeaderNames.AUTHORIZATION, "Basic " + credentials)
                .requestEntity(String.class);

        assertThat(response, is("principal:alice"));
    }

    @Test
    @DisplayName("SecurityContext - Bearer auth with X-User-Name header")
    void testBearerAuthWithUserNameHeader() {
        String response = client.get("/security/principal")
                .header(HeaderNames.AUTHORIZATION, "Bearer some-token")
                .header(HeaderNames.create("X-User-Name"), "bob")
                .requestEntity(String.class);

        assertThat(response, is("principal:bob"));
    }

    // ========== Role Tests ==========

    @Test
    @DisplayName("SecurityContext - no roles returns false")
    void testNoRoles() {
        String response = client.get("/security/role/admin")
                .requestEntity(String.class);

        assertThat(response, is("role:admin=false"));
    }

    @Test
    @DisplayName("SecurityContext - X-User-Roles header grants role")
    void testUserHasRole() {
        String response = client.get("/security/role/admin")
                .header(HeaderNames.create("X-User-Roles"), "admin")
                .requestEntity(String.class);

        assertThat(response, is("role:admin=true"));
    }

    @Test
    @DisplayName("SecurityContext - multiple roles in header")
    void testMultipleRoles() {
        String response = client.get("/security/roles-check")
                .header(HeaderNames.create("X-User-Roles"), "admin, user")
                .requestEntity(String.class);

        assertThat(response, is("admin:true,user:true,guest:false"));
    }

    @Test
    @DisplayName("SecurityContext - role not in header")
    void testRoleNotInHeader() {
        String response = client.get("/security/role/admin")
                .header(HeaderNames.create("X-User-Roles"), "user,guest")
                .requestEntity(String.class);

        assertThat(response, is("role:admin=false"));
    }

    // ========== isSecure Tests ==========

    @Test
    @DisplayName("SecurityContext - HTTP is not secure")
    void testHttpNotSecure() {
        String response = client.get("/security/secure")
                .requestEntity(String.class);

        // Test server uses HTTP, not HTTPS
        assertThat(response, is("secure:false"));
    }

    // ========== Authentication Scheme Tests ==========

    @Test
    @DisplayName("SecurityContext - no auth returns null scheme")
    void testNoAuthScheme() {
        String response = client.get("/security/scheme")
                .requestEntity(String.class);

        assertThat(response, is("scheme:null"));
    }

    @Test
    @DisplayName("SecurityContext - Basic auth returns BASIC scheme")
    void testBasicAuthScheme() {
        String credentials = Base64.getEncoder().encodeToString("user:pass".getBytes());

        String response = client.get("/security/scheme")
                .header(HeaderNames.AUTHORIZATION, "Basic " + credentials)
                .requestEntity(String.class);

        assertThat(response, is("scheme:BASIC"));
    }

    @Test
    @DisplayName("SecurityContext - Bearer auth returns BEARER scheme")
    void testBearerAuthScheme() {
        String response = client.get("/security/scheme")
                .header(HeaderNames.AUTHORIZATION, "Bearer some-jwt-token")
                .requestEntity(String.class);

        assertThat(response, is("scheme:BEARER"));
    }

    @Test
    @DisplayName("SecurityContext - Digest auth returns DIGEST scheme")
    void testDigestAuthScheme() {
        String response = client.get("/security/scheme")
                .header(HeaderNames.AUTHORIZATION, "Digest username=\"user\"")
                .requestEntity(String.class);

        assertThat(response, is("scheme:DIGEST"));
    }

    // ========== Combined Info Tests ==========

    @Test
    @DisplayName("SecurityContext - full security info with Basic auth")
    void testFullSecurityInfoBasicAuth() {
        String credentials = Base64.getEncoder().encodeToString("charlie:password".getBytes());

        String response = client.get("/security/info")
                .header(HeaderNames.AUTHORIZATION, "Basic " + credentials)
                .requestEntity(String.class);

        assertThat(response, is("user:charlie,scheme:BASIC,secure:false"));
    }

    @Test
    @DisplayName("SecurityContext - anonymous user info")
    void testAnonymousSecurityInfo() {
        String response = client.get("/security/info")
                .requestEntity(String.class);

        assertThat(response, is("user:anonymous,scheme:none,secure:false"));
    }

    // ========== Protected Endpoint Tests ==========

    @Test
    @DisplayName("SecurityContext - admin endpoint with admin role")
    void testAdminEndpointWithRole() {
        String credentials = Base64.getEncoder().encodeToString("admin-user:pass".getBytes());

        String response = client.get("/security/admin")
                .header(HeaderNames.AUTHORIZATION, "Basic " + credentials)
                .header(HeaderNames.create("X-User-Roles"), "admin")
                .requestEntity(String.class);

        assertThat(response, is("admin-access-granted:admin-user"));
    }

    @Test
    @DisplayName("SecurityContext - admin endpoint without role returns 403")
    void testAdminEndpointWithoutRole() {
        String credentials = Base64.getEncoder().encodeToString("regular-user:pass".getBytes());

        try (HttpClientResponse response = client.get("/security/admin")
                .header(HeaderNames.AUTHORIZATION, "Basic " + credentials)
                .header(HeaderNames.create("X-User-Roles"), "user")
                .request()) {

            assertThat(response.status(), is(Status.FORBIDDEN_403));
        }
    }

    @Test
    @DisplayName("SecurityContext - admin endpoint with no auth returns 403")
    void testAdminEndpointNoAuth() {
        try (HttpClientResponse response = client.get("/security/admin")
                .request()) {

            assertThat(response.status(), is(Status.FORBIDDEN_403));
        }
    }
}
