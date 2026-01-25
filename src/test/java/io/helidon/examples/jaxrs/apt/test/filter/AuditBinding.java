package io.helidon.examples.jaxrs.apt.test.filter;

import jakarta.ws.rs.NameBinding;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Name binding annotation for audit-related filters.
 * Filters and methods annotated with @AuditBinding will be bound together.
 */
@NameBinding
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface AuditBinding {
}
