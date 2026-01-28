package io.helidon.jaxrs.filters.test.runtime;

import io.helidon.jaxrs.filters.FilterContext;
import io.helidon.jaxrs.filters.HttpHeadersProxy;
import io.helidon.jaxrs.filters.ResourceInfoProxy;
import io.helidon.jaxrs.filters.SecurityContextProxy;
import io.helidon.jaxrs.filters.UriInfoProxy;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class FilterContextTest {

    @Test
    void testFilterEntryMatches() {
        FilterContext.FilterEntry<ContainerRequestFilter> global =
                new FilterContext.FilterEntry<>(new NoOpFilter(), Set.of());
        assertThat(global.matches(Set.of("binding")), is(true));

        FilterContext.FilterEntry<ContainerRequestFilter> bound =
                new FilterContext.FilterEntry<>(new NoOpFilter(), Set.of("one", "two"));
        assertThat(bound.matches(Set.of("two")), is(true));
        assertThat(bound.matches(Set.of("other")), is(false));
    }

    @Test
    void testContextInjection() {
        FilterContext.clearFieldCache();

        ContextFieldFilter filter = new ContextFieldFilter();
        FilterContext.injectContextProxies(filter);

        assertThat(filter.uriInfo, is(UriInfoProxy.INSTANCE));
        assertThat(filter.httpHeaders, is(HttpHeadersProxy.INSTANCE));
        assertThat(filter.securityContext, is(SecurityContextProxy.INSTANCE));
        assertThat(filter.resourceInfo, is(ResourceInfoProxy.INSTANCE));
    }

    @Test
    void testContextInjectionNoField() {
        FilterContext.clearFieldCache();

        NoContextFilter filter = new NoContextFilter();
        FilterContext.injectContextProxy(filter, UriInfo.class, UriInfoProxy.INSTANCE);
        FilterContext.injectContextProxy(filter, UriInfo.class, UriInfoProxy.INSTANCE);

        assertThat(filter.marker, notNullValue());
    }

    private static final class NoOpFilter implements ContainerRequestFilter {
        @Override
        public void filter(ContainerRequestContext requestContext) throws IOException {
        }
    }

    private static final class NoContextFilter implements ContainerRequestFilter {
        private final String marker = "ok";

        @Override
        public void filter(ContainerRequestContext requestContext) throws IOException {
        }
    }

    private static final class ContextFieldFilter implements ContainerRequestFilter {
        @Context
        private UriInfo uriInfo;
        @Context
        private HttpHeaders httpHeaders;
        @Context
        private SecurityContext securityContext;
        @Context
        private ResourceInfo resourceInfo;

        @Override
        public void filter(ContainerRequestContext requestContext) throws IOException {
        }
    }
}


