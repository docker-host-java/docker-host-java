package com.github.dockerhostjava.connection;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class Connection {

    private boolean tlsVerify;
    private TlsCerts tlsCerts;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private URI address;
    private HttpClient client;
    private String commandText;

    public Connection tlsVerify(boolean tlsVerify) {
        this.tlsVerify = tlsVerify;
        return this;
    }

    public Connection tlsCerts(TlsCerts tlsCerts) {
        this.tlsCerts = tlsCerts;
        return this;
    }

    public Connection connectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    public Connection connect(HostObject host) {
        String protocol = tlsVerify ? "https://" : "http://";
        this.address = URI.create(protocol + host.host() + ":" + host.port());
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        ping(connectTimeout);
        return this;
    }

    public Connection command(String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command is empty");
        }
        this.commandText = command;
        return this;
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

    // For Test
    String commandText() {
        return commandText;
    }
}
