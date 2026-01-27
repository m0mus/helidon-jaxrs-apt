package io.helidon.examples.jaxrs.apt.runtime;

import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.WriterInterceptor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Manages filters, interceptors, and exception mappers with support for name bindings.
 */
public class FilterContext {

    // Cache of @Context ResourceInfo fields per filter class
    // Stores Field objects or NO_FIELD sentinel when no field exists
    private static final Map<Class<?>, Object> resourceInfoFieldCache = new ConcurrentHashMap<>();
    private static final Object NO_FIELD = new Object(); // Sentinel for "no field found"

    private final List<ContainerRequestFilter> preMatchingRequestFilters = new ArrayList<>();
    private final List<FilterEntry<ContainerRequestFilter>> requestFilters = new ArrayList<>();
    private final List<FilterEntry<ContainerResponseFilter>> responseFilters = new ArrayList<>();
    private final List<InterceptorEntry<ReaderInterceptor>> readerInterceptors = new ArrayList<>();
    private final List<InterceptorEntry<WriterInterceptor>> writerInterceptors = new ArrayList<>();
    private final List<ExceptionMapperEntry<?>> exceptionMappers = new ArrayList<>();

    // Pre-matching filters (no name binding support)
    public void addPreMatchingRequestFilter(ContainerRequestFilter filter) {
        preMatchingRequestFilters.add(filter);
    }

    public List<ContainerRequestFilter> getPreMatchingRequestFilters() {
        return preMatchingRequestFilters;
    }

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

    // Reader interceptors
    public void addReaderInterceptor(ReaderInterceptor interceptor) {
        readerInterceptors.add(new InterceptorEntry<>(interceptor, Set.of()));
    }

    public void addReaderInterceptor(ReaderInterceptor interceptor, Set<String> nameBindings) {
        readerInterceptors.add(new InterceptorEntry<>(interceptor, nameBindings));
    }

    public List<InterceptorEntry<ReaderInterceptor>> getReaderInterceptorsWithBindings() {
        return readerInterceptors;
    }

    // Writer interceptors
    public void addWriterInterceptor(WriterInterceptor interceptor) {
        writerInterceptors.add(new InterceptorEntry<>(interceptor, Set.of()));
    }

    public void addWriterInterceptor(WriterInterceptor interceptor, Set<String> nameBindings) {
        writerInterceptors.add(new InterceptorEntry<>(interceptor, nameBindings));
    }

    public List<InterceptorEntry<WriterInterceptor>> getWriterInterceptorsWithBindings() {
        return writerInterceptors;
    }

    // Exception mappers
    public <T extends Throwable> void addExceptionMapper(ExceptionMapper<T> mapper, Class<T> exceptionType) {
        exceptionMappers.add(new ExceptionMapperEntry<>(mapper, exceptionType));
    }

    /**
     * Find an exception mapper for the given exception.
     * Returns null if no mapper is found.
     * Searches for the most specific mapper (exact type match first, then superclasses).
     */
    @SuppressWarnings("unchecked")
    public <T extends Throwable> ExceptionMapper<T> findExceptionMapper(T exception) {
        Class<?> exceptionClass = exception.getClass();

        // First try exact match
        for (ExceptionMapperEntry<?> entry : exceptionMappers) {
            if (entry.exceptionType().equals(exceptionClass)) {
                return (ExceptionMapper<T>) entry.mapper();
            }
        }

        // Then try superclass matches (find the most specific one)
        ExceptionMapperEntry<?> bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;

        for (ExceptionMapperEntry<?> entry : exceptionMappers) {
            if (entry.exceptionType().isAssignableFrom(exceptionClass)) {
                int distance = getInheritanceDistance(exceptionClass, entry.exceptionType());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestMatch = entry;
                }
            }
        }

        return bestMatch != null ? (ExceptionMapper<T>) bestMatch.mapper() : null;
    }

    private int getInheritanceDistance(Class<?> from, Class<?> to) {
        int distance = 0;
        Class<?> current = from;
        while (current != null && !current.equals(to)) {
            distance++;
            current = current.getSuperclass();
        }
        return current != null ? distance : Integer.MAX_VALUE;
    }

    public List<ExceptionMapperEntry<?>> getExceptionMappers() {
        return exceptionMappers;
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
     * Entry for an interceptor with its name bindings.
     */
    public record InterceptorEntry<T>(T interceptor, Set<String> nameBindings) {
        /**
         * Check if this interceptor should apply to a method with the given bindings.
         */
        public boolean matches(Set<String> methodBindings) {
            if (nameBindings.isEmpty()) {
                return true; // Global interceptor applies to all
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
     * Entry for an exception mapper with its exception type.
     */
    public record ExceptionMapperEntry<T extends Throwable>(ExceptionMapper<T> mapper, Class<T> exceptionType) {}

    /**
     * Inject ResourceInfo into a filter's @Context ResourceInfo field.
     * This allows filters to access information about the matched resource method.
     *
     * @param filter the filter instance
     * @param resourceInfo the ResourceInfo to inject
     */
    public static void injectResourceInfo(Object filter, ResourceInfo resourceInfo) {
        if (filter == null || resourceInfo == null) {
            return;
        }

        Class<?> filterClass = filter.getClass();

        // Check cache first
        Object cached = resourceInfoFieldCache.get(filterClass);
        if (cached == NO_FIELD) {
            return; // Already determined no field exists
        }

        Field field = (cached instanceof Field) ? (Field) cached : findResourceInfoField(filterClass);

        if (field != null) {
            try {
                field.set(filter, resourceInfo);
            } catch (IllegalAccessException e) {
                // Log but don't fail - injection is best-effort
                System.err.println("Warning: Cannot inject ResourceInfo into " + filterClass.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Find the @Context ResourceInfo field in a filter class.
     */
    private static Field findResourceInfoField(Class<?> filterClass) {
        // Search class hierarchy
        Class<?> current = filterClass;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(Context.class) && ResourceInfo.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    resourceInfoFieldCache.put(filterClass, field);
                    return field;
                }
            }
            current = current.getSuperclass();
        }

        // No field found - cache this fact using sentinel
        resourceInfoFieldCache.put(filterClass, NO_FIELD);
        return null;
    }

    /**
     * Clear the field cache. Useful for testing.
     */
    public static void clearFieldCache() {
        resourceInfoFieldCache.clear();
    }
}
