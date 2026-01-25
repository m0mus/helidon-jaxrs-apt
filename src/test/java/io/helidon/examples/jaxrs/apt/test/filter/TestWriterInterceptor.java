package io.helidon.examples.jaxrs.apt.test.filter;

import io.helidon.examples.jaxrs.apt.test.util.FilterOrderTracker;
import jakarta.annotation.Priority;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;

import java.io.IOException;

/**
 * Writer interceptor for testing body writing interception.
 */
@Provider
@Priority(100)
public class TestWriterInterceptor implements WriterInterceptor {

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
        FilterOrderTracker.recordWriterInterceptor("TestWriterInterceptor");
        context.proceed();
    }
}
