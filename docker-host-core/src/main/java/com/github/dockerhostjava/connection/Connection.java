package com.github.dockerhostjava.connection;

import javax.net.ssl.SSLContext;
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
        return host.open(this);
    }

    void establish(HostObject host) {
        if (host.host() == null || host.host().isBlank() || host.port() <= 0 || host.port() > 65535) {
            throw new IllegalArgumentException(
                    "host address is incomplete: host=" + host.host() + " port=" + host.port());
        }
        String protocol = tlsVerify ? "https://" : "http://";
        this.address = URI.create(protocol + host.host() + ":" + host.port());
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(connectTimeout);
        if (tlsVerify) {
            builder.sslContext(sslContext());
        }
        this.client = builder.build();
        ping(connectTimeout);
    }

    public Connection command(String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command is empty: uri=" + addressText());
        }
        this.commandText = command;
        return this;
    }

    private SSLContext sslContext() {
        if (tlsCerts == null) {
            throw new IllegalStateException(
                    "tls certs are missing: uri=" + address
                            + " tlsVerify=true requires ca.pem, cert.pem, and key.pem");
        }
        try {
            return tlsCerts.sslContext();
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "tls certs could not be applied: uri=" + address
                            + " ca=" + tlsCerts.caPem()
                            + " cert=" + tlsCerts.certPem()
                            + " key=" + tlsCerts.keyPem()
                            + " error=" + e.getMessage(),
                    e);
        }
    }

    private void ping(Duration timeout) {
        URI pingUri = address.resolve("/_ping");
        HttpRequest request = HttpRequest.newBuilder(pingUri)
                .timeout(timeout)
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body() == null ? "" : response.body().trim();
            if (response.statusCode() != 200 || !body.equals("OK")) {
                String apiVersion = response.headers().firstValue("Api-Version").orElse("");
                String message = "ping failed: uri=" + pingUri
                        + " timeout=" + timeout.toMillis() + "ms"
                        + " status=" + response.statusCode()
                        + " body=" + body;
                if (!apiVersion.isBlank()) {
                    message += " apiVersion=" + apiVersion;
                }
                throw new IllegalStateException(message);
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "ping failed: uri=" + pingUri
                            + " timeout=" + timeout.toMillis() + "ms"
                            + " error=" + e.getClass().getSimpleName() + ": " + e.getMessage(),
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "ping interrupted: uri=" + pingUri
                            + " timeout=" + timeout.toMillis() + "ms",
                    e);
        }
    }

    private String addressText() {
        return address == null ? "not connected" : address.toString();
    }

    // For Test
    String commandText() {
        return commandText;
    }
}
