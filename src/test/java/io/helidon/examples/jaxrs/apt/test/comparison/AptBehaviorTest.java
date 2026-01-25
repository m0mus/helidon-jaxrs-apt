package io.helidon.examples.jaxrs.apt.test.comparison;

import io.helidon.examples.jaxrs.apt.UserResource$$JaxRsRouting;
import io.helidon.examples.jaxrs.apt.test.resources.TestParameterResource$$JaxRsRouting;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.http.HttpRouting;

/**
 * Behavioral tests running against APT-generated routing code.
 *
 * <p>This test class runs the same tests as {@link AbstractBehaviorTest}
 * against the compile-time generated Helidon routing code.
 */
@ServerTest
class AptBehaviorTest extends AbstractBehaviorTest {

    private final WebClient client;

    AptBehaviorTest(WebClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        // Register APT-generated routing
        new UserResource$$JaxRsRouting().register(routing);
        new TestParameterResource$$JaxRsRouting().register(routing);
    }

    @Override
    protected WebClient getClient() {
        return client;
    }
}
