package io.helidon.examples.jaxrs.apt.test.integration;

import io.helidon.examples.jaxrs.apt.runtime.JaxRsFilterFeature;
import io.helidon.examples.jaxrs.apt.runtime.JaxRsFilterSupport;
import io.helidon.examples.jaxrs.apt.test.util.FilterOrderTracker;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

/**
 * Integration tests for JAX-RS filters without JAX-RS resources.
 */
@ServerTest
class FilterOnlyIntegrationTest {

    private final WebClient client;

    FilterOnlyIntegrationTest(WebClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        routing.addFeature(JaxRsFilterFeature::new);
        routing.get("/plain", (req, res) -> res.send("plain"));
    }

    @BeforeEach
    void setUp() {
        JaxRsFilterSupport.resetJaxRsRoutingForTests();
        FilterOrderTracker.clear();
    }

    @Test
    @DisplayName("Filters run without any JAX-RS resources")
    void testFiltersRunWithoutJaxrsResources() {
        client.get("/plain").requestEntity(String.class);

        List<String> requestOrder = FilterOrderTracker.getRequestFilterOrder();
        assertThat(requestOrder, contains(
                "PreMatchingTestFilter",
                "Priority100Filter",
                "OrderTrackingFilter",
                "Priority300Filter"
        ));

        List<String> responseOrder = FilterOrderTracker.getResponseFilterOrder();
        assertThat(responseOrder, contains(
                "Priority300Filter",
                "OrderTrackingFilter",
                "Priority100Filter"
        ));
    }
}
