package io.helidon.examples.jaxrs.apt.runtime;

import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.WriterInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Manages filters, interceptors, and exception mappers with support for name bindings.
 */
public class FilterContext {

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
}
