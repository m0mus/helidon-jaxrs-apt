package io.helidon.examples.jaxrs.apt.runtime;

import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;

/**
 * Auto-register JAX-RS filters without requiring JAX-RS resources.
 */
public class JaxRsFilterFeature implements HttpFeature {

    @Override
    public void setup(HttpRouting.Builder routing) {
        JaxRsFilterSupport.register(routing);
    }
}
