package com.github.dockerhostjava.connection;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineHttpTest {

    @Test
    void connectSucceedsWhenPingReturnsOk() throws IOException {
        HttpServer server = server("/_ping", 200, "OK");
        server.start();
        try {
            EngineHttp engineHttp = EngineHttp.connect(connection(server));
            assertNotNull(engineHttp);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void connectSucceedsWhenPingBodyHasTrailingNewline() throws IOException {
        HttpServer server = server("/_ping", 200, "OK\n");
        server.start();
        try {
            EngineHttp engineHttp = EngineHttp.connect(connection(server));
            assertNotNull(engineHttp);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void connectFailsWhenPingStatusIsNotOk() throws IOException {
        HttpServer server = server("/_ping", 500, "nope");
        server.start();
        try {
            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> EngineHttp.connect(connection(server)));
            assertTrue(error.getMessage().contains("status=500"));
        } finally {
            server.stop(0);
        }
    }

    private static ConnectionMaker connection(HttpServer server) {
        return new ConnectionMaker()
                .host("127.0.0.1")
                .port(server.getAddress().getPort())
                .tlsVerify(false);
    }

    private static HttpServer server(String path, int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        return server;
    }
}
