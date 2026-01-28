package io.helidon.examples.jaxrs.apt.test.runtime;

import io.helidon.examples.jaxrs.apt.runtime.FilterContext;
import io.helidon.examples.jaxrs.apt.runtime.JaxRsFilterOnlyFilter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.HttpEntryPoint;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class FilterOnlyFilterTest {

    @Test
    void testEmptyFilterContextProceeds() {
        JaxRsFilterOnlyFilter filter = new JaxRsFilterOnlyFilter(new FilterContext());

        AtomicBoolean proceeded = new AtomicBoolean(false);
        FilterChain chain = () -> proceeded.set(true);
        filter.filter(chain, null, null);

        assertThat(proceeded.get(), is(true));
    }

    @Test
    void testEmptyFilterContextInterceptorProceeds() throws Exception {
        JaxRsFilterOnlyFilter filter = new JaxRsFilterOnlyFilter(new FilterContext());

        AtomicBoolean proceeded = new AtomicBoolean(false);
        HttpEntryPoint.Interceptor.Chain chain = new HttpEntryPoint.Interceptor.Chain() {
            @Override
            public void proceed(ServerRequest req, ServerResponse res) {
                proceeded.set(true);
            }
        };

        filter.proceed(null, chain, null, null);

        assertThat(proceeded.get(), is(true));
    }
}
