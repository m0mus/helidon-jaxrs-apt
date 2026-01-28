package io.helidon.jaxrs.filters;

import io.helidon.webserver.http.HttpRouting;
import jakarta.annotation.Priority;
import jakarta.ws.rs.NameBinding;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Registers JAX-RS filters without requiring any JAX-RS resources.
 */
public final class JaxRsFilterSupport {

    private static final int DEFAULT_PRIORITY = 5000;
    private JaxRsFilterSupport() {
    }

    /**
     * Register JAX-RS filters discovered via {@link ServiceLoader}.
     *
     * @param routing routing builder to register filters with
     */
    public static void register(HttpRouting.Builder routing) {
        register(routing, Thread.currentThread().getContextClassLoader());
    }

    /**
     * Register JAX-RS filters discovered via {@link ServiceLoader}.
     *
     * @param routing routing builder to register filters with
     * @param classLoader class loader used for service discovery
     */
    public static void register(HttpRouting.Builder routing, ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");
        register(routing, loadProviders(classLoader));
    }

    /**
     * Register JAX-RS filters from the provided instances.
     *
     * @param routing routing builder to register filters with
     * @param providers filter/provider instances to register
     */
    public static void register(HttpRouting.Builder routing, Iterable<?> providers) {
        Objects.requireNonNull(routing, "routing");
        Objects.requireNonNull(providers, "providers");

        SimpleRuntimeDelegate.init();

        FilterContext filterContext = new FilterContext();
        List<ProviderEntry<ContainerRequestFilter>> preMatchingFilters = new ArrayList<>();
        List<ProviderEntry<ContainerRequestFilter>> requestFilters = new ArrayList<>();
        List<ProviderEntry<ContainerResponseFilter>> responseFilters = new ArrayList<>();

        for (Object provider : providers) {
            if (provider == null) {
                continue;
            }
            Class<?> providerClass = provider.getClass();
            Set<String> bindings = nameBindings(providerClass);
            int priority = priority(providerClass);

            if (provider instanceof ContainerRequestFilter requestFilter) {
                FilterContext.injectContextProxies(requestFilter);
                if (providerClass.getAnnotation(PreMatching.class) != null) {
                    preMatchingFilters.add(new ProviderEntry<>(requestFilter, priority, bindings));
                } else {
                    requestFilters.add(new ProviderEntry<>(requestFilter, priority, bindings));
                }
            }

            if (provider instanceof ContainerResponseFilter responseFilter) {
                FilterContext.injectContextProxies(responseFilter);
                responseFilters.add(new ProviderEntry<>(responseFilter, priority, bindings));
            }
        }

        preMatchingFilters.sort(Comparator.comparingInt(
                (ProviderEntry<ContainerRequestFilter> entry) -> entry.priority()));
        requestFilters.sort(Comparator.comparingInt(
                (ProviderEntry<ContainerRequestFilter> entry) -> entry.priority()));
        responseFilters.sort(Comparator.comparingInt(
                (ProviderEntry<ContainerResponseFilter> entry) -> entry.priority()).reversed());

        routing.addFilter(JaxRsContextFilter.INSTANCE);

        if (!preMatchingFilters.isEmpty()) {
            List<ContainerRequestFilter> preMatchingList = new ArrayList<>();
            for (ProviderEntry<ContainerRequestFilter> entry : preMatchingFilters) {
                preMatchingList.add(entry.provider());
            }
            routing.addFilter(new JaxRsPreMatchingFilter(preMatchingList));
        }

        for (ProviderEntry<ContainerRequestFilter> entry : requestFilters) {
            if (entry.bindings().isEmpty()) {
                filterContext.addRequestFilter(entry.provider());
            } else {
                filterContext.addRequestFilter(entry.provider(), entry.bindings());
            }
        }

        for (ProviderEntry<ContainerResponseFilter> entry : responseFilters) {
            if (entry.bindings().isEmpty()) {
                filterContext.addResponseFilter(entry.provider());
            } else {
                filterContext.addResponseFilter(entry.provider(), entry.bindings());
            }
        }

        if (!filterContext.getRequestFiltersWithBindings().isEmpty()
                || !filterContext.getResponseFiltersWithBindings().isEmpty()) {
            routing.addFilter(new JaxRsFilter(filterContext));
        }
    }


    private static List<Object> loadProviders(ClassLoader classLoader) {
        java.util.LinkedHashMap<Class<?>, Object> providers = new java.util.LinkedHashMap<>();
        for (ContainerRequestFilter filter : ServiceLoader.load(ContainerRequestFilter.class, classLoader)) {
            providers.putIfAbsent(filter.getClass(), filter);
        }
        for (ContainerResponseFilter filter : ServiceLoader.load(ContainerResponseFilter.class, classLoader)) {
            providers.putIfAbsent(filter.getClass(), filter);
        }
        return new ArrayList<>(providers.values());
    }

    private static int priority(Class<?> providerClass) {
        Priority priority = providerClass.getAnnotation(Priority.class);
        return priority != null ? priority.value() : DEFAULT_PRIORITY;
    }

    private static Set<String> nameBindings(Class<?> providerClass) {
        Set<String> bindings = new HashSet<>();
        for (Annotation annotation : providerClass.getAnnotations()) {
            if (annotation.annotationType().getAnnotation(NameBinding.class) != null) {
                bindings.add(annotation.annotationType().getName());
            }
        }
        return bindings;
    }

    private record ProviderEntry<T>(T provider, int priority, Set<String> bindings) {
    }
}



