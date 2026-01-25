package io.helidon.examples.jaxrs.apt.test.util;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;

import java.util.Map;
import java.util.function.Consumer;

/**
 * HTTP client abstraction for testing JAX-RS endpoints.
 * Provides fluent API for making HTTP requests and verifying responses.
 */
public class TestClient {

    private final WebClient client;

    /**
     * Create a test client for the given base URI.
     *
     * @param baseUri the base URI (e.g., "http://localhost:8080")
     */
    public TestClient(String baseUri) {
        this.client = WebClient.builder()
                .baseUri(baseUri)
                .build();
    }

    /**
     * Create a test client for the given host and port.
     *
     * @param host the host
     * @param port the port
     */
    public TestClient(String host, int port) {
        this("http://" + host + ":" + port);
    }

    /**
     * Make a GET request.
     *
     * @param path the path
     * @return the response
     */
    public TestResponse get(String path) {
        return request(Method.GET, path, null, null, null);
    }

    /**
     * Make a GET request with custom headers.
     *
     * @param path    the path
     * @param headers the headers to add
     * @return the response
     */
    public TestResponse get(String path, Map<String, String> headers) {
        return request(Method.GET, path, null, headers, null);
    }

    /**
     * Make a GET request with query parameters.
     *
     * @param path        the path
     * @param queryParams the query parameters
     * @return the response
     */
    public TestResponse getWithQuery(String path, Map<String, String> queryParams) {
        return request(Method.GET, path, null, null, queryParams);
    }

    /**
     * Make a POST request with JSON body.
     *
     * @param path the path
     * @param body the JSON body
     * @return the response
     */
    public TestResponse post(String path, String body) {
        return request(Method.POST, path, body, Map.of("Content-Type", "application/json"), null);
    }

    /**
     * Make a POST request with form data.
     *
     * @param path     the path
     * @param formData the form data
     * @return the response
     */
    public TestResponse postForm(String path, Map<String, String> formData) {
        StringBuilder body = new StringBuilder();
        formData.forEach((key, value) -> {
            if (body.length() > 0) {
                body.append("&");
            }
            body.append(key).append("=").append(value);
        });
        return request(Method.POST, path, body.toString(),
                Map.of("Content-Type", "application/x-www-form-urlencoded"), null);
    }

    /**
     * Make a PUT request with JSON body.
     *
     * @param path the path
     * @param body the JSON body
     * @return the response
     */
    public TestResponse put(String path, String body) {
        return request(Method.PUT, path, body, Map.of("Content-Type", "application/json"), null);
    }

    /**
     * Make a DELETE request.
     *
     * @param path the path
     * @return the response
     */
    public TestResponse delete(String path) {
        return request(Method.DELETE, path, null, null, null);
    }

    /**
     * Make a custom request.
     *
     * @param method      the HTTP method
     * @param path        the path
     * @param body        the body (may be null)
     * @param headers     the headers (may be null)
     * @param queryParams the query parameters (may be null)
     * @return the response
     */
    public TestResponse request(Method method, String path, String body,
                                Map<String, String> headers, Map<String, String> queryParams) {
        HttpClientRequest request = client.method(method).path(path);

        if (headers != null) {
            headers.forEach((name, value) -> request.header(HeaderNames.create(name), value));
        }

        if (queryParams != null) {
            queryParams.forEach(request::queryParam);
        }

        HttpClientResponse response;
        if (body != null) {
            response = request.submit(body);
        } else {
            response = request.request();
        }

        return new TestResponse(response);
    }

    /**
     * Make a request with a cookie.
     *
     * @param method     the HTTP method
     * @param path       the path
     * @param cookieName the cookie name
     * @param cookieValue the cookie value
     * @return the response
     */
    public TestResponse requestWithCookie(Method method, String path, String cookieName, String cookieValue) {
        HttpClientRequest request = client.method(method).path(path)
                .header(HeaderNames.COOKIE, cookieName + "=" + cookieValue);
        return new TestResponse(request.request());
    }

    /**
     * Test response wrapper with fluent assertions.
     */
    public static class TestResponse {
        private final HttpClientResponse response;
        private String bodyCache;

        TestResponse(HttpClientResponse response) {
            this.response = response;
        }

        /**
         * Get the HTTP status code.
         *
         * @return the status code
         */
        public int status() {
            return response.status().code();
        }

        /**
         * Get the response body as string.
         *
         * @return the body
         */
        public String body() {
            if (bodyCache == null) {
                bodyCache = response.as(String.class);
            }
            return bodyCache;
        }

        /**
         * Get a response header value.
         *
         * @param name the header name
         * @return the header value or null
         */
        public String header(String name) {
            return response.headers().first(HeaderNames.create(name)).orElse(null);
        }

        /**
         * Check if the status is OK (200).
         *
         * @return true if status is 200
         */
        public boolean isOk() {
            return status() == Status.OK_200.code();
        }

        /**
         * Check if the status is Not Found (404).
         *
         * @return true if status is 404
         */
        public boolean isNotFound() {
            return status() == Status.NOT_FOUND_404.code();
        }

        /**
         * Check if the status is No Content (204).
         *
         * @return true if status is 204
         */
        public boolean isNoContent() {
            return status() == Status.NO_CONTENT_204.code();
        }

        /**
         * Get the underlying response.
         *
         * @return the HTTP response
         */
        public HttpClientResponse raw() {
            return response;
        }
    }
}
