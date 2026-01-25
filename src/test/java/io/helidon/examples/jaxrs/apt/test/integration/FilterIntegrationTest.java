package io.helidon.examples.jaxrs.apt.test.integration;

import io.helidon.examples.jaxrs.apt.test.resources.TestFilterResource$$JaxRsRouting;
import io.helidon.examples.jaxrs.apt.test.util.FilterOrderTracker;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.http.HttpRouting;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for filter behavior and priority ordering.
 */
@ServerTest
class FilterIntegrationTest {

    private final WebClient client;

    FilterIntegrationTest(WebClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        new TestFilterResource$$JaxRsRouting().register(routing);
    }

    @BeforeEach
    void setUp() {
        FilterOrderTracker.clear();
    }

    @Test
    @DisplayName("Request filters execute in priority order (lower number = first)")
    void testRequestFilterPriorityOrder() {
        client.get("/filter/test").requestEntity(String.class);

        List<String> order = FilterOrderTracker.getRequestFilterOrder();

        // Pre-matching filter should be first (priority 50)
        assertThat(order.get(0), is("PreMatchingTestFilter"));

        // Then Priority100Filter (priority 100)
        assertThat(order.get(1), is("Priority100Filter"));

        // Then OrderTrackingFilter (priority 200)
        assertThat(order.get(2), is("OrderTrackingFilter"));

        // Then Priority300Filter (priority 300)
        assertThat(order.get(3), is("Priority300Filter"));
    }

    @Test
    @DisplayName("Response filters execute in reverse priority order (higher number = first)")
    void testResponseFilterPriorityOrder() {
        client.get("/filter/test").requestEntity(String.class);

        List<String> order = FilterOrderTracker.getResponseFilterOrder();

        // Response filters run in reverse order
        // Priority300Filter should be first (priority 300, reversed)
        assertThat(order.get(0), is("Priority300Filter"));

        // Then OrderTrackingFilter (priority 200)
        assertThat(order.get(1), is("OrderTrackingFilter"));

        // Then Priority100Filter (priority 100)
        assertThat(order.get(2), is("Priority100Filter"));
    }

    @Test
    @DisplayName("@PreMatching filter executes before route matching")
    void testPreMatchingFilter() {
        client.get("/filter/prematching").requestEntity(String.class);

        List<String> order = FilterOrderTracker.getRequestFilterOrder();

        // PreMatchingTestFilter should always be first
        assertThat(order, hasItem("PreMatchingTestFilter"));
        assertThat(order.indexOf("PreMatchingTestFilter"), is(0));
    }

    @Test
    @DisplayName("@NameBinding filter only applies to annotated endpoints")
    void testNameBoundFilter() {
        // Request to non-audited endpoint
        FilterOrderTracker.clear();
        client.get("/filter/test").requestEntity(String.class);
        List<String> nonAuditedOrder = FilterOrderTracker.getRequestFilterOrder();

        // AuditFilter should NOT be in the list for non-audited endpoint
        assertThat(nonAuditedOrder, not(hasItem("AuditFilter")));

        // Request to audited endpoint
        FilterOrderTracker.clear();
        client.get("/filter/audited").requestEntity(String.class);
        List<String> auditedOrder = FilterOrderTracker.getRequestFilterOrder();

        // AuditFilter SHOULD be in the list for audited endpoint
        assertThat(auditedOrder, hasItem("AuditFilter"));
    }

    @Test
    @DisplayName("Name-bound filter executes at correct position based on priority")
    void testNameBoundFilterPriority() {
        client.get("/filter/audited").requestEntity(String.class);

        List<String> order = FilterOrderTracker.getRequestFilterOrder();

        // AuditFilter has priority 250, should be between OrderTrackingFilter (200) and Priority300Filter (300)
        int auditIndex = order.indexOf("AuditFilter");
        int orderTrackingIndex = order.indexOf("OrderTrackingFilter");
        int priority300Index = order.indexOf("Priority300Filter");

        assertThat(auditIndex, greaterThan(orderTrackingIndex));
        assertThat(auditIndex, lessThan(priority300Index));
    }

    @Test
    @DisplayName("All global filters execute on every request")
    void testGlobalFilters() {
        client.get("/filter/test").requestEntity(String.class);

        List<String> order = FilterOrderTracker.getRequestFilterOrder();

        // All global filters should be present
        assertThat(order, hasItems(
                "PreMatchingTestFilter",
                "Priority100Filter",
                "OrderTrackingFilter",
                "Priority300Filter"
        ));
    }

    @Test
    @DisplayName("Filter executes on error response")
    void testFilterOnError() {
        FilterOrderTracker.clear();

        var response = client.get("/filter/error").request();

        assertThat(response.status().code(), is(404));

        // Filters should still have executed
        List<String> requestOrder = FilterOrderTracker.getRequestFilterOrder();
        assertThat(requestOrder, not(empty()));

        // Response filters should also have executed
        List<String> responseOrder = FilterOrderTracker.getResponseFilterOrder();
        assertThat(responseOrder, not(empty()));
    }

    @Test
    @DisplayName("Request and response filter execution is complete cycle")
    void testFilterCompleteCycle() {
        client.get("/filter/test").requestEntity(String.class);

        List<String> allEvents = FilterOrderTracker.getAllEvents();

        // Should have both REQUEST and RESPONSE events
        long requestEvents = allEvents.stream().filter(e -> e.startsWith("REQUEST:")).count();
        long responseEvents = allEvents.stream().filter(e -> e.startsWith("RESPONSE:")).count();

        assertThat(requestEvents, greaterThan(0L));
        assertThat(responseEvents, greaterThan(0L));

        // All request events should come before response events
        int lastRequestIndex = -1;
        int firstResponseIndex = allEvents.size();

        for (int i = 0; i < allEvents.size(); i++) {
            if (allEvents.get(i).startsWith("REQUEST:")) {
                lastRequestIndex = i;
            }
            if (allEvents.get(i).startsWith("RESPONSE:") && i < firstResponseIndex) {
                firstResponseIndex = i;
            }
        }

        assertThat(lastRequestIndex, lessThan(firstResponseIndex));
    }
}
