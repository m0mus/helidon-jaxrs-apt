package io.helidon.examples.jaxrs.apt.test.integration;

import io.helidon.examples.jaxrs.apt.runtime.JaxRsFilterFeature;
import io.helidon.examples.jaxrs.apt.test.util.CoverageTracker;
import io.helidon.http.HeaderNames;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@ServerTest
class FilterOnlyCoverageTest {

    private final WebClient client;

    FilterOnlyCoverageTest(WebClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        routing.addFeature(JaxRsFilterFeature::new);
        routing.get("/coverage/{id}", (req, res) -> {
            res.header("X-Existing", "from-handler");
            res.send("ok");
        });
        routing.post("/coverage/{id}", (req, res) -> {
            res.header("X-Existing", "from-handler");
            res.send("ok");
        });
    }

    @BeforeEach
    void clearCoverage() {
        CoverageTracker.clear();
    }

    @Test
    void testPreMatchingCoverage() {
        var response = client.get("/coverage/123?first=one&first=two")
                .header(HeaderNames.create("X-Coverage-Mode"), "pre")
                .header(HeaderNames.ACCEPT, "text/plain, invalid/type")
                .header(HeaderNames.ACCEPT_LANGUAGE, "en-US, fr-FR; q=0.8")
                .header(HeaderNames.CONTENT_TYPE, "text/plain")
                .header(HeaderNames.CONTENT_LANGUAGE, "en")
                .header(HeaderNames.CONTENT_LENGTH, "invalid")
                .header(HeaderNames.COOKIE, "session=abc; theme=light")
                .request();

        assertThat(response.status().code(), is(200));
        response.close();

        assertThat(CoverageTracker.value("pre.enter"), is("true"));
        assertThat(CoverageTracker.value("pre.prop"), is("value"));
        assertThat(CoverageTracker.value("pre.prop.removed"), is("true"));
        assertThat(CoverageTracker.value("pre.header.added"), is("yes"));
        assertThat(CoverageTracker.value("pre.accept.count"), is("2"));
        assertThat(CoverageTracker.value("pre.language.count"), is("2"));
        assertThat(CoverageTracker.value("pre.cookie"), is("abc"));
        assertThat(CoverageTracker.value("pre.length"), is("0"));
        assertThat(CoverageTracker.value("pre.mediaType"), containsString("text/plain"));
        assertThat(CoverageTracker.value("pre.uri.original.path"), notNullValue());
        assertThat(CoverageTracker.value("pre.uri.modified.path"), notNullValue());
        assertThat(CoverageTracker.value("pre.getRequest"), is("unsupported"));
        assertThat(CoverageTracker.value("pre.setEntityStream"), is("unsupported"));
        assertThat(CoverageTracker.value("pre.security.scheme"), is("CUSTOM"));
    }

    @Test
    void testRequestCoverage() {
        var response = client.post("/coverage/123?query=one")
                .header(HeaderNames.create("X-Coverage-Mode"), "request")
                .header(HeaderNames.create("X-Coverage-Header"), "header-value")
                .header(HeaderNames.ACCEPT, "text/plain")
                .header(HeaderNames.ACCEPT_LANGUAGE, "en-US")
                .header(HeaderNames.CONTENT_TYPE, "text/plain")
                .header(HeaderNames.CONTENT_LANGUAGE, "en")
                .header(HeaderNames.CONTENT_LENGTH, "7")
                .header(HeaderNames.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                .header(HeaderNames.create("X-User-Roles"), "admin, user")
                .submit("payload");
        assertThat(response.status().code(), is(200));
        response.close();

        assertThat(CoverageTracker.value("request.method"), is("POST"));
        assertThat(CoverageTracker.value("request.header"), is("header-value"));
        assertThat(CoverageTracker.value("request.accept.count"), is("1"));
        assertThat(CoverageTracker.value("request.language.count"), is("1"));
        assertThat(CoverageTracker.value("request.mediaType"), containsString("text/plain"));
        assertThat(CoverageTracker.value("request.language"), containsString("en"));
        assertThat(CoverageTracker.value("request.length"), is("7"));
        assertThat(CoverageTracker.value("request.hasEntity"), is("true"));
        assertThat(CoverageTracker.value("request.prop"), is("value"));
        assertThat(CoverageTracker.value("request.prop.removed"), is("true"));
        assertThat(CoverageTracker.value("request.security.scheme"), is("BASIC"));
        assertThat(CoverageTracker.value("request.security.user"), is("user"));
        assertThat(CoverageTracker.value("request.security.role"), is("true"));
        assertThat(CoverageTracker.value("request.getRequest"), is("unsupported"));
        assertThat(CoverageTracker.value("request.setRequestUri"), is("unsupported"));
        assertThat(CoverageTracker.value("request.setRequestUri.base"), is("unsupported"));
        assertThat(CoverageTracker.value("request.setMethod"), is("unsupported"));
        assertThat(CoverageTracker.value("request.setEntityStream"), is("unsupported"));
        assertThat(CoverageTracker.value("request.resourceInfo.exists"), is("true"));
        assertThat(CoverageTracker.value("request.resourceInfo.property"), is("true"));
    }

    @Test
    void testResponseCoverage() {
        var response = client.get("/coverage/123")
                .header(HeaderNames.create("X-Coverage-Mode"), "response")
                .request();

        assertThat(response.status().code(), is(202));
        assertThat(response.headers().first(HeaderNames.create("X-Coverage-Response")).orElse(null), is("yes"));

        assertThat(CoverageTracker.value("response.status"), is("202"));
        assertThat(CoverageTracker.value("response.statusInfo"), is("Accepted"));
        assertThat(CoverageTracker.value("response.header"), is("value"));
        assertThat(CoverageTracker.value("response.mediaType"), containsString("text/plain"));
        assertThat(CoverageTracker.value("response.hasEntity"), is("true"));
        assertThat(CoverageTracker.value("response.entity"), is("entity2"));
        assertThat(CoverageTracker.value("response.getEntityStream"), is("unsupported"));
        assertThat(CoverageTracker.value("response.setEntityStream"), is("unsupported"));

        response.close();
    }

    @Test
    void testSecurityContextCoverage() {
        client.get("/coverage/123")
                .header(HeaderNames.create("X-Coverage-Mode"), "request")
                .header(HeaderNames.create("X-Coverage-Auth"), "basic-invalid")
                .header(HeaderNames.AUTHORIZATION, "Basic invalid")
                .request()
                .close();

        client.get("/coverage/123")
                .header(HeaderNames.create("X-Coverage-Mode"), "request")
                .header(HeaderNames.create("X-Coverage-Auth"), "bearer")
                .header(HeaderNames.AUTHORIZATION, "Bearer token")
                .header(HeaderNames.create("X-User-Name"), "bearer-user")
                .header(HeaderNames.create("X-User-Roles"), "admin")
                .request()
                .close();

        client.get("/coverage/123")
                .header(HeaderNames.create("X-Coverage-Mode"), "request")
                .header(HeaderNames.create("X-Coverage-Auth"), "digest")
                .header(HeaderNames.AUTHORIZATION, "Digest abc")
                .request()
                .close();

        assertThat(CoverageTracker.value("auth.basic-invalid.scheme"), is("BASIC"));
        assertThat(CoverageTracker.value("auth.basic-invalid.user"), is("null"));
        assertThat(CoverageTracker.value("auth.bearer.scheme"), is("BEARER"));
        assertThat(CoverageTracker.value("auth.bearer.user"), is("bearer-user"));
        assertThat(CoverageTracker.value("auth.bearer.role"), is("true"));
        assertThat(CoverageTracker.value("auth.bearer.secure"), is("false"));
        assertThat(CoverageTracker.value("auth.digest.scheme"), is("DIGEST"));
        assertThat(CoverageTracker.value("auth.digest.user"), is("null"));
    }
}
