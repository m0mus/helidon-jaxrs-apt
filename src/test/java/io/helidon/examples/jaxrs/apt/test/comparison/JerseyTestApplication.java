package io.helidon.examples.jaxrs.apt.test.comparison;

import io.helidon.examples.jaxrs.apt.UserResource;
import io.helidon.examples.jaxrs.apt.test.resources.TestParameterResource;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

import java.util.Set;

/**
 * JAX-RS Application for Jersey comparison tests.
 * Registers the same resources used by the APT implementation.
 */
@ApplicationPath("/")
public class JerseyTestApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(
                UserResource.class,
                TestParameterResource.class,
                NotFoundExceptionMapper.class
        );
    }
}
