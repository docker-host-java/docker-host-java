package com.github.dockerhostjava.connection.HttpConnection.logic;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class EnginePinger {

    private EnginePinger() {
    }

    /** GET /_ping 응답이 OK인지 확인한다. */
    public static void ping(HttpClient client, URI address, Duration timeout) {
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
}
