package com.github.dockerhostjava.connection;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

public class EngineHttp {

    private final URI address;
    private final Path certPath;
    private final HttpClient client;

    private EngineHttp(URI address, Path certPath, HttpClient client) {
        this.address = address;
        this.certPath = certPath;
        this.client = client;
    }

    public static EngineHttp connect(ConnectionMaker connection) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(connection.connectTimeout())
                .build();
        EngineHttp engineHttp = new EngineHttp(
                URI.create(connection.connectAddress()),
                connection.certPath(),
                client);
        engineHttp.ping(connection.connectTimeout());
        return engineHttp;
    }

    private void ping(Duration timeout) {
        HttpRequest request = HttpRequest.newBuilder(address.resolve("/_ping"))
                .timeout(timeout)
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body() == null ? "" : response.body().trim();
            if (response.statusCode() != 200 || !body.equals("OK")) {
                throw new IllegalStateException(
                        "ping failed: status=" + response.statusCode() + " body=" + body);
            }
        } catch (IOException e) {
            throw new IllegalStateException("ping failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ping interrupted", e);
        }
    }
}
