package io.helidon.jaxrs.filters.test.unit;

import io.helidon.jaxrs.filters.FilterContext;
import io.helidon.jaxrs.filters.JaxRsFilter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.HttpEntryPoint;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class JaxRsFilterTest {

    @Test
    void testEmptyFilterContextProceeds() {
        JaxRsFilter filter = new JaxRsFilter(new FilterContext());

        AtomicBoolean proceeded = new AtomicBoolean(false);
        FilterChain chain = () -> proceeded.set(true);
        filter.filter(chain, null, null);

        assertThat(proceeded.get(), is(true));
    }

    @Test
    void testEmptyFilterContextInterceptorProceeds() throws Exception {
        JaxRsFilter filter = new JaxRsFilter(new FilterContext());

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



