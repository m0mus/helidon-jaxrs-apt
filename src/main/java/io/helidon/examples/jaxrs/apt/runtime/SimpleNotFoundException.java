package io.helidon.examples.jaxrs.apt.runtime;

/**
 * Simple not-found exception that doesn't use JAX-RS Response (avoiding RuntimeDelegate lookup).
 * This is a lightweight alternative to jakarta.ws.rs.NotFoundException for use with the APT-generated code.
 */
public class SimpleNotFoundException extends RuntimeException {

    private final int status;

    public SimpleNotFoundException(String message) {
        super(message);
        this.status = 404;
    }

    public SimpleNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.status = 404;
    }

    public int getStatus() {
        return status;
    }
}
