package com.github.dockerhostjava.connection;

import com.github.dockerhostjava.host.HttpHostObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionTest {

    @Test
    void connectSucceedsWhenPingReturnsOk() throws IOException {
        HttpServer server = server("/_ping", 200, "OK");
        server.start();
        try {
            HttpConnection connection = HttpConnection.connectPlain(host(server));
            assertNotNull(connection);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void connectSucceedsWhenPingBodyHasTrailingNewline() throws IOException {
        HttpServer server = server("/_ping", 200, "OK\n");
        server.start();
        try {
            HttpConnection connection = HttpConnection.connectPlain(host(server));
            assertNotNull(connection);
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
                    () -> HttpConnection.connectPlain(host(server)));
            assertTrue(error.getMessage().contains("status=500"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void connectTlsFailsWithoutCerts() {
        HttpHostObject host = new HttpHostObject().host("127.0.0.1").port(2376);
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> HttpConnection.connectTls(host, null));
        assertTrue(error.getMessage().contains("tls certs are missing"));
        assertNull(host.connection());
    }

    @Test
    void connectTlsFailsWhenTheHostIsAlreadyPlain() throws IOException {
        HttpServer server = server("/_ping", 200, "OK");
        server.start();
        try {
            HttpHostObject host = host(server);
            HttpConnection.connectPlain(host);
            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> HttpConnection.connectTls(host, new TlsCerts()));
            assertTrue(error.getMessage().contains("already connected with other tls settings"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void commandFailsBeforeConnecting() {
        HttpHostObject host = new HttpHostObject().host("127.0.0.1").port(2375);
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> host.command(List.of("docker ps")));
        assertTrue(error.getMessage().contains("docker engine is not connected"));
    }

    @Test
    void makeConnectionKeepsPlainOnTheNewAddress() throws IOException {
        HttpServer first = server("/_ping", 200, "OK");
        Captured captured = new Captured();
        HttpServer second = engine(captured, 200, "[]");
        first.start();
        second.start();
        try {
            HttpHostObject host = host(first);
            HttpConnection before = HttpConnection.connectPlain(host);
            HttpConnection after = host.makeConnection("127.0.0.1", second.getAddress().getPort()).orElseThrow();
            assertNotSame(before, after);
            assertEquals(Optional.of(List.of("[]")), host.command(List.of("docker ps")));
            assertEquals("/containers/json", captured.path);
        } finally {
            first.stop(0);
            second.stop(0);
        }
    }

    @Test
    void commandSendsDockerPsToTheEngine() throws IOException {
        Captured captured = new Captured();
        HttpServer server = engine(captured, 200, "[]");
        server.start();
        try {
            HttpHostObject host = host(server);
            HttpConnection.connectPlain(host);
            Optional<?> body = host.command(List.of("docker ps"));
            assertEquals(Optional.of(List.of("[]")), body);
            assertEquals("GET", captured.method);
            assertEquals("/containers/json", captured.path);
            assertEquals(null, captured.query);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void commandSendsDockerPsAllToTheEngine() throws IOException {
        Captured captured = new Captured();
        HttpServer server = engine(captured, 200, "[]");
        server.start();
        try {
            HttpHostObject host = host(server);
            HttpConnection.connectPlain(host);
            Optional<?> body = host.command(List.of("docker ps", "docker ps -a"));
            assertEquals(Optional.of(List.of("[]", "[]")), body);
            assertEquals("/containers/json", captured.path);
            assertEquals(2, captured.queries.size());
            assertEquals(null, captured.queries.get(0));
            assertEquals("all=true", captured.queries.get(1));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void commandFailsWhenTheCliIsEmpty() {
        HttpHostObject host = new HttpHostObject().host("127.0.0.1").port(2375);
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> host.command(List.of()));
        assertTrue(error.getMessage().contains("command is empty"));
    }

    @Test
    void commandFailsWhenTheCliIsUnsupported() throws IOException {
        HttpServer server = server("/_ping", 200, "OK");
        server.start();
        try {
            HttpHostObject host = host(server);
            HttpConnection.connectPlain(host);
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> host.command(List.of("docker build")));
            assertTrue(error.getMessage().contains("command is unsupported: build"));
        } finally {
            server.stop(0);
        }
    }

    private static HttpHostObject host(HttpServer server) {
        return new HttpHostObject()
                .host("127.0.0.1")
                .port(server.getAddress().getPort());
    }

    private static HttpServer server(String path, int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> write(exchange, status, body));
        return server;
    }

    private static HttpServer engine(Captured captured, int status, String body) throws IOException {
        HttpServer server = server("/_ping", 200, "OK");
        server.createContext("/containers/json", exchange -> {
            captured.method = exchange.getRequestMethod();
            captured.path = exchange.getRequestURI().getPath();
            captured.query = exchange.getRequestURI().getRawQuery();
            captured.queries.add(exchange.getRequestURI().getRawQuery());
            write(exchange, status, body);
        });
        return server;
    }

    private static void write(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static final class Captured {
        private volatile String method;
        private volatile String path;
        private volatile String query;
        private final List<String> queries = new ArrayList<>();
    }
}
