package io.helidon.jaxrs.filters.runtime;

import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages JAX-RS request/response filters with support for name bindings.
 *
 * <p>This class is responsible for:
 * <ul>
 *   <li>Storing and retrieving request/response filters</li>
 *   <li>Name binding matching for selective filter application</li>
 *   <li>Injecting @Context proxies into filter instances</li>
 * </ul>
 */
public class FilterContext {

    private static final Logger LOGGER = System.getLogger(FilterContext.class.getName());

    // Cache of @Context fields per filter class, keyed by (filterClass, contextType)
    private static final Map<FieldCacheKey, Object> contextFieldCache = new ConcurrentHashMap<>();
    private static final Object NO_FIELD = new Object(); // Sentinel for "no field found"

    // Map of context types to their proxy instances
    private static final Map<Class<?>, Object> CONTEXT_PROXIES = new HashMap<>();

    static {
        CONTEXT_PROXIES.put(UriInfo.class, UriInfoProxy.INSTANCE);
        CONTEXT_PROXIES.put(HttpHeaders.class, HttpHeadersProxy.INSTANCE);
        CONTEXT_PROXIES.put(SecurityContext.class, SecurityContextProxy.INSTANCE);
        CONTEXT_PROXIES.put(ResourceInfo.class, ResourceInfoProxy.INSTANCE);
    }

    // Cache key for field lookups
    private record FieldCacheKey(Class<?> filterClass, Class<?> contextType) {}

    private final List<FilterEntry<ContainerRequestFilter>> requestFilters = new ArrayList<>();
    private final List<FilterEntry<ContainerResponseFilter>> responseFilters = new ArrayList<>();

    // Request filters
    public void addRequestFilter(ContainerRequestFilter filter) {
        requestFilters.add(new FilterEntry<>(filter, Set.of()));
    }

    public void addRequestFilter(ContainerRequestFilter filter, Set<String> nameBindings) {
        requestFilters.add(new FilterEntry<>(filter, nameBindings));
    }

    public List<FilterEntry<ContainerRequestFilter>> getRequestFiltersWithBindings() {
        return requestFilters;
    }

    // Response filters
    public void addResponseFilter(ContainerResponseFilter filter) {
        responseFilters.add(new FilterEntry<>(filter, Set.of()));
    }

    public void addResponseFilter(ContainerResponseFilter filter, Set<String> nameBindings) {
        responseFilters.add(new FilterEntry<>(filter, nameBindings));
    }

    public List<FilterEntry<ContainerResponseFilter>> getResponseFiltersWithBindings() {
        return responseFilters;
    }


    /**
     * Entry for a filter with its name bindings.
     */
    public record FilterEntry<T>(T filter, Set<String> nameBindings) {
        /**
         * Check if this filter should apply to a method with the given bindings.
         * A filter matches if:
         * - It has no name bindings (global filter), OR
         * - At least one of its bindings matches the method's bindings
         */
        public boolean matches(Set<String> methodBindings) {
            if (nameBindings.isEmpty()) {
                return true; // Global filter applies to all
            }
            for (String binding : nameBindings) {
                if (methodBindings.contains(binding)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Inject all supported @Context proxies into a filter instance.
     * This should be called once at filter construction time.
     *
     * <p>Supported context types:
     * <ul>
     *   <li>{@link UriInfo} - Request URI information</li>
     *   <li>{@link HttpHeaders} - Request headers</li>
     *   <li>{@link SecurityContext} - Security context</li>
     *   <li>{@link ResourceInfo} - Matched resource class and method</li>
     * </ul>
     *
     * @param filter the filter instance to inject into
     */
    public static void injectContextProxies(Object filter) {
        if (filter == null) {
            return;
        }

        for (Map.Entry<Class<?>, Object> entry : CONTEXT_PROXIES.entrySet()) {
            injectContextProxy(filter, entry.getKey(), entry.getValue());
        }
    }

    /**
     * Inject a specific @Context proxy into a filter instance.
     *
     * @param filter the filter instance
     * @param contextType the context interface type (e.g., UriInfo.class)
     * @param proxy the proxy instance to inject
     */
    public static void injectContextProxy(Object filter, Class<?> contextType, Object proxy) {
        if (filter == null || proxy == null) {
            return;
        }

        Class<?> filterClass = filter.getClass();
        FieldCacheKey cacheKey = new FieldCacheKey(filterClass, contextType);

        // Check cache first
        Object cached = contextFieldCache.get(cacheKey);
        if (cached == NO_FIELD) {
            return; // Already determined no field exists
        }

        Field field = (cached instanceof Field) ? (Field) cached : findContextField(filterClass, contextType, cacheKey);

        if (field != null) {
            try {
                field.set(filter, proxy);
            } catch (IllegalAccessException e) {
                // Log but don't fail - injection is best-effort
                LOGGER.log(Level.WARNING, "Cannot inject {0} into {1}: {2}",
                        contextType.getSimpleName(), filterClass.getName(), e.getMessage());
            }
        }
    }

    /**
     * Find a @Context field of the specified type in a filter class.
     */
    private static Field findContextField(Class<?> filterClass, Class<?> contextType, FieldCacheKey cacheKey) {
        // Search class hierarchy
        Class<?> current = filterClass;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(Context.class) && contextType.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    contextFieldCache.put(cacheKey, field);
                    return field;
                }
            }
            current = current.getSuperclass();
        }

        // No field found - cache this fact using sentinel
        contextFieldCache.put(cacheKey, NO_FIELD);
        return null;
    }

    /**
     * Clear the field cache. Useful for testing.
     */
    public static void clearFieldCache() {
        contextFieldCache.clear();
    }
}

