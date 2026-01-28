package io.helidon.examples.jaxrs.apt.runtime;

import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpEntryPoint;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.util.List;
import java.util.Objects;

/**
 * Handler wrapper that runs HttpEntryPoint interceptors with route metadata.
 */
public class JaxRsEntryPointHandler implements Handler {

    private static final InterceptionContext INTERCEPTION_CONTEXT = createInterceptionContext();

    private final Handler delegate;
    private final JaxRsRouteInfo routeInfo;
    private final List<HttpEntryPoint.Interceptor> interceptors;
    private final InterceptionContext interceptionContext;

    /**
     * Create a new entry point handler.
     *
     * @param delegate the actual route handler
     * @param routeInfo route metadata for post-matching filters
     * @param interceptors interceptors to execute around the handler
     */
    public JaxRsEntryPointHandler(Handler delegate,
                                  JaxRsRouteInfo routeInfo,
                                  List<HttpEntryPoint.Interceptor> interceptors) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.routeInfo = Objects.requireNonNull(routeInfo, "routeInfo");
        this.interceptors = List.copyOf(Objects.requireNonNull(interceptors, "interceptors"));
        this.interceptionContext = INTERCEPTION_CONTEXT;
    }

    @Override
    public void handle(ServerRequest req, ServerResponse res) throws Exception {
        req.context().register(JaxRsRouteInfo.class, routeInfo);
        new InterceptorChain(interceptors, delegate, interceptionContext).proceed(req, res);
    }

    private static final class InterceptorChain implements HttpEntryPoint.Interceptor.Chain {
        private final List<HttpEntryPoint.Interceptor> interceptors;
        private final Handler delegate;
        private final InterceptionContext interceptionContext;
        private int index;

        private InterceptorChain(List<HttpEntryPoint.Interceptor> interceptors,
                                 Handler delegate,
                                 InterceptionContext interceptionContext) {
            this.interceptors = interceptors;
            this.delegate = delegate;
            this.interceptionContext = interceptionContext;
        }

        @Override
        public void proceed(ServerRequest req, ServerResponse res) throws Exception {
            if (index < interceptors.size()) {
                HttpEntryPoint.Interceptor interceptor = interceptors.get(index++);
                interceptor.proceed(interceptionContext, this, req, res);
                return;
            }
            delegate.handle(req, res);
        }
    }

    private static InterceptionContext createInterceptionContext() {
        TypeName handlerType = TypeName.create(JaxRsEntryPointHandler.class);
        TypedElementInfo elementInfo = TypedElementInfo.builder()
                .typeName(handlerType)
                .elementName("handle")
                .kind(ElementKind.METHOD)
                .accessModifier(AccessModifier.PUBLIC)
                .build();

        return InterceptionContext.builder()
                .serviceInfo(new EntryPointServiceInfo(handlerType))
                .elementInfo(elementInfo)
                .build();
    }

    private static final class EntryPointServiceInfo implements ServiceInfo {
        private final TypeName handlerType;

        private EntryPointServiceInfo(TypeName handlerType) {
            this.handlerType = handlerType;
        }

        @Override
        public TypeName serviceType() {
            return handlerType;
        }

        @Override
        public TypeName descriptorType() {
            return handlerType;
        }

        @Override
        public TypeName scope() {
            return TypeName.create("jakarta.inject.Singleton");
        }
    }
}
