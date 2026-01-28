package io.helidon.examples.jaxrs.apt.test.integration;

import io.helidon.examples.jaxrs.apt.runtime.JaxRsFilterSupport;
import io.helidon.examples.jaxrs.apt.test.filter.OrderTrackingFilter;
import io.helidon.examples.jaxrs.apt.test.filter.PreMatchingTestFilter;
import io.helidon.examples.jaxrs.apt.test.filter.Priority100Filter;
import io.helidon.examples.jaxrs.apt.test.filter.Priority300Filter;
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
import static org.hamcrest.Matchers.is;

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
        List<Object> providers = List.of(
                new PreMatchingTestFilter(),
                new Priority100Filter(),
                new OrderTrackingFilter(),
                new Priority300Filter()
        );
        JaxRsFilterSupport.register(routing, providers);

        routing.get("/plain", (req, res) -> res.send("plain"));
    }

    @BeforeEach
    void setUp() {
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
