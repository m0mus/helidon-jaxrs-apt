package io.helidon.examples.jaxrs.apt.test.util;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Captures values from coverage-focused filters.
 */
public final class CoverageTracker {

    private static final Map<String, String> VALUES = new ConcurrentHashMap<>();
    private static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    private CoverageTracker() {
    }

    public static void record(String key, String value) {
        String safeValue = value == null ? "null" : value;
        VALUES.put(key, safeValue);
        EVENTS.add(key + "=" + safeValue);
    }

    public static String value(String key) {
        return VALUES.get(key);
    }

    public static List<String> events() {
        return Collections.unmodifiableList(EVENTS);
    }

    public static void clear() {
        VALUES.clear();
        EVENTS.clear();
    }
}
