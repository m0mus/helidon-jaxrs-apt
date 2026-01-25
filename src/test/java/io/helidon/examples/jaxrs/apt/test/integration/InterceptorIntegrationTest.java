package io.helidon.examples.jaxrs.apt.test.integration;

import io.helidon.examples.jaxrs.apt.test.resources.TestFilterResource$$JaxRsRouting;
import io.helidon.examples.jaxrs.apt.test.util.FilterOrderTracker;
import io.helidon.http.HeaderNames;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.http.HttpRouting;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for reader and writer interceptors.
 */
@ServerTest
class InterceptorIntegrationTest {

    private final WebClient client;

    InterceptorIntegrationTest(WebClient client) {
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
    @DisplayName("ReaderInterceptor invoked when reading request body")
    void testReaderInterceptorInvoked() {
        String json = "{\"message\":\"test\"}";

        var response = client.post("/filter/body")
                .header(HeaderNames.CONTENT_TYPE, "application/json")
                .submit(json);

        String body = response.as(String.class);
        assertThat(body, is("received:test"));

        List<String> readerOrder = FilterOrderTracker.getReaderInterceptorOrder();
        assertThat(readerOrder, hasItem("TestReaderInterceptor"));
    }

    @Test
    @DisplayName("WriterInterceptor invoked when writing response body")
    void testWriterInterceptorInvoked() {
        String response = client.get("/filter/json").requestEntity(String.class);

        assertThat(response, containsString("test-message"));

        List<String> writerOrder = FilterOrderTracker.getWriterInterceptorOrder();
        assertThat(writerOrder, hasItem("TestWriterInterceptor"));
    }

    @Test
    @DisplayName("Reader interceptor not invoked for requests without body")
    void testReaderInterceptorNotInvokedWithoutBody() {
        client.get("/filter/test").requestEntity(String.class);

        List<String> readerOrder = FilterOrderTracker.getReaderInterceptorOrder();
        assertThat(readerOrder, is(empty()));
    }

    @Test
    @DisplayName("Writer interceptor invoked on JSON response")
    void testWriterInterceptorOnJsonResponse() {
        FilterOrderTracker.clear();

        String response = client.get("/filter/json").requestEntity(String.class);

        // Response should be valid JSON
        assertThat(response, containsString("message"));
        assertThat(response, containsString("test-message"));

        // Writer interceptor should have been invoked
        List<String> writerOrder = FilterOrderTracker.getWriterInterceptorOrder();
        assertThat(writerOrder, not(empty()));
    }

    @Test
    @DisplayName("Interceptors execute in correct order with filters")
    void testInterceptorAndFilterOrder() {
        String json = "{\"message\":\"test\"}";

        var response = client.post("/filter/body")
                .header(HeaderNames.CONTENT_TYPE, "application/json")
                .submit(json);

        response.as(String.class); // consume response

        List<String> allEvents = FilterOrderTracker.getAllEvents();

        // Request filters should execute before reader interceptor
        int firstRequestFilter = findFirstEventIndex(allEvents, "REQUEST:");
        int readerInterceptor = findFirstEventIndex(allEvents, "READER:");

        assertThat(firstRequestFilter, lessThan(readerInterceptor));

        // Writer interceptor should execute before response filters
        int writerInterceptor = findFirstEventIndex(allEvents, "WRITER:");
        int firstResponseFilter = findFirstEventIndex(allEvents, "RESPONSE:");

        assertThat(writerInterceptor, lessThan(firstResponseFilter));
    }

    @Test
    @DisplayName("Full request cycle with body - filters and interceptors")
    void testFullRequestCycleWithBody() {
        String json = "{\"message\":\"hello\"}";

        FilterOrderTracker.clear();

        var response = client.post("/filter/body")
                .header(HeaderNames.CONTENT_TYPE, "application/json")
                .submit(json);

        String body = response.as(String.class);
        assertThat(body, is("received:hello"));

        List<String> allEvents = FilterOrderTracker.getAllEvents();

        // Should have all event types
        boolean hasRequest = allEvents.stream().anyMatch(e -> e.startsWith("REQUEST:"));
        boolean hasReader = allEvents.stream().anyMatch(e -> e.startsWith("READER:"));
        boolean hasWriter = allEvents.stream().anyMatch(e -> e.startsWith("WRITER:"));
        boolean hasResponse = allEvents.stream().anyMatch(e -> e.startsWith("RESPONSE:"));

        assertThat("Should have request filter events", hasRequest, is(true));
        assertThat("Should have reader interceptor events", hasReader, is(true));
        assertThat("Should have writer interceptor events", hasWriter, is(true));
        assertThat("Should have response filter events", hasResponse, is(true));
    }

    private int findFirstEventIndex(List<String> events, String prefix) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }
}
