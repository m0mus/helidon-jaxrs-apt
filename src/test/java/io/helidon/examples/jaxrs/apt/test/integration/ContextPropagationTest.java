package io.helidon.examples.jaxrs.apt.test.integration;

import io.helidon.common.context.Contexts;
import io.helidon.examples.jaxrs.apt.runtime.JaxRsContextFilter;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.http.HttpRouting;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Tests verifying that Helidon Context propagation works correctly.
 * This is a prerequisite for request-scoped @Context injection in filters.
 */
@ServerTest
class ContextPropagationTest {

    private final WebClient client;

    // Capture what we see in the handler
    private static final AtomicReference<String> capturedFromContext = new AtomicReference<>();

    ContextPropagationTest(WebClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        // Register our context filter FIRST
        routing.addFilter(JaxRsContextFilter.INSTANCE);

        // Register a handler that uses Contexts.context()
        routing.get("/context-test", (req, res) -> {
            // Register something in the request's context
            req.context().register("TestMarker", "hello-from-context");

            // Try to retrieve it via Contexts.context()
            String value = Contexts.context()
                    .flatMap(ctx -> ctx.get("TestMarker", String.class))
                    .orElse("NOT_FOUND");

            capturedFromContext.set(value);
            res.send(value);
        });
    }

    @Test
    void testContextsContextReturnsRequestContext() {
        var response = client.get("/context-test").requestEntity(String.class);

        // The handler should have been able to retrieve the value via Contexts.context()
        assertThat(response, is("hello-from-context"));
        assertThat(capturedFromContext.get(), is("hello-from-context"));
    }

    @Test
    void testContextIsolationBetweenRequests() {
        // First request
        capturedFromContext.set(null);
        var response1 = client.get("/context-test").requestEntity(String.class);
        assertThat(response1, is("hello-from-context"));

        // Second request should have its own context (not see previous request's data)
        capturedFromContext.set(null);
        var response2 = client.get("/context-test").requestEntity(String.class);
        assertThat(response2, is("hello-from-context"));
    }
}
