package io.helidon.examples.jaxrs.apt.test.resources;

/**
 * Custom business exception for testing ExceptionMapper.
 */
public class CustomBusinessException extends RuntimeException {

    private final String errorCode;

    public CustomBusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
