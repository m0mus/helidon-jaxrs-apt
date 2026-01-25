package io.helidon.examples.jaxrs.apt.test.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe tracker for recording filter and interceptor execution order.
 * Used in tests to verify correct filter priority ordering.
 */
public final class FilterOrderTracker {

    private static final List<String> REQUEST_FILTER_ORDER = new CopyOnWriteArrayList<>();
    private static final List<String> RESPONSE_FILTER_ORDER = new CopyOnWriteArrayList<>();
    private static final List<String> READER_INTERCEPTOR_ORDER = new CopyOnWriteArrayList<>();
    private static final List<String> WRITER_INTERCEPTOR_ORDER = new CopyOnWriteArrayList<>();
    private static final List<String> ALL_EVENTS = new CopyOnWriteArrayList<>();

    private FilterOrderTracker() {
    }

    /**
     * Record a request filter execution.
     *
     * @param filterName the name of the filter
     */
    public static void recordRequestFilter(String filterName) {
        REQUEST_FILTER_ORDER.add(filterName);
        ALL_EVENTS.add("REQUEST:" + filterName);
    }

    /**
     * Record a response filter execution.
     *
     * @param filterName the name of the filter
     */
    public static void recordResponseFilter(String filterName) {
        RESPONSE_FILTER_ORDER.add(filterName);
        ALL_EVENTS.add("RESPONSE:" + filterName);
    }

    /**
     * Record a reader interceptor execution.
     *
     * @param interceptorName the name of the interceptor
     */
    public static void recordReaderInterceptor(String interceptorName) {
        READER_INTERCEPTOR_ORDER.add(interceptorName);
        ALL_EVENTS.add("READER:" + interceptorName);
    }

    /**
     * Record a writer interceptor execution.
     *
     * @param interceptorName the name of the interceptor
     */
    public static void recordWriterInterceptor(String interceptorName) {
        WRITER_INTERCEPTOR_ORDER.add(interceptorName);
        ALL_EVENTS.add("WRITER:" + interceptorName);
    }

    /**
     * Get the order of request filter executions.
     *
     * @return unmodifiable list of filter names in execution order
     */
    public static List<String> getRequestFilterOrder() {
        return Collections.unmodifiableList(new ArrayList<>(REQUEST_FILTER_ORDER));
    }

    /**
     * Get the order of response filter executions.
     *
     * @return unmodifiable list of filter names in execution order
     */
    public static List<String> getResponseFilterOrder() {
        return Collections.unmodifiableList(new ArrayList<>(RESPONSE_FILTER_ORDER));
    }

    /**
     * Get the order of reader interceptor executions.
     *
     * @return unmodifiable list of interceptor names in execution order
     */
    public static List<String> getReaderInterceptorOrder() {
        return Collections.unmodifiableList(new ArrayList<>(READER_INTERCEPTOR_ORDER));
    }

    /**
     * Get the order of writer interceptor executions.
     *
     * @return unmodifiable list of interceptor names in execution order
     */
    public static List<String> getWriterInterceptorOrder() {
        return Collections.unmodifiableList(new ArrayList<>(WRITER_INTERCEPTOR_ORDER));
    }

    /**
     * Get all recorded events in order.
     *
     * @return unmodifiable list of all events
     */
    public static List<String> getAllEvents() {
        return Collections.unmodifiableList(new ArrayList<>(ALL_EVENTS));
    }

    /**
     * Clear all recorded data. Call this before each test.
     */
    public static void clear() {
        REQUEST_FILTER_ORDER.clear();
        RESPONSE_FILTER_ORDER.clear();
        READER_INTERCEPTOR_ORDER.clear();
        WRITER_INTERCEPTOR_ORDER.clear();
        ALL_EVENTS.clear();
    }
}
