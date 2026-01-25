package io.helidon.examples.jaxrs.apt.test.comparison;

import io.helidon.microprofile.server.Server;
import io.helidon.webclient.api.WebClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Behavioral tests running against Jersey/Helidon MP implementation.
 *
 * <p>This test class runs the same tests as {@link AbstractBehaviorTest}
 * against the Jersey runtime implementation for comparison.
 *
 * <p>This test is only enabled when running with the jersey-comparison profile:
 * {@code mvn test -Pjersey-comparison}
 *
 * <p>Note: This test requires Helidon MP dependencies which are only available
 * when the jersey-comparison profile is active.
 */
@EnabledIfSystemProperty(named = "jersey.comparison.enabled", matches = "true")
class JerseyBehaviorTest extends AbstractBehaviorTest {

    private static WebClient client;
    private static Server server;

    @BeforeAll
    static void startServer() {
        // Start Helidon MP server with Jersey
        server = Server.builder()
                .port(0) // Random available port
                .addApplication(JerseyTestApplication.class)
                .build()
                .start();

        int port = server.port();

        client = WebClient.builder()
                .baseUri("http://localhost:" + port)
                .build();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Override
    protected WebClient getClient() {
        return client;
    }
}
