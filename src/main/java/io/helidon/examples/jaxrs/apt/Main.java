package io.helidon.examples.jaxrs.apt;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;

/**
 * Application entry point demonstrating JAX-RS APT code generation.
 *
 * <p>The {@code UserResource$$JaxRsRouting} class is generated at compile time
 * from JAX-RS annotations, providing reflection-free routing.
 */
public class Main {

    public static void main(String[] args) {
        WebServer server = WebServer.builder()
                .port(8080)
                .routing(Main::routing)
                .build()
                .start();

        System.out.println("Server started at http://localhost:" + server.port());
    }

    static void routing(HttpRouting.Builder routing) {
        new UserResource$$JaxRsRouting().register(routing);
    }
}
