package com.github.dockerhostjava.connection.HttpConnection.logic;

import com.github.dockerhostjava.connection.DockerCli.logic.EngineCall;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class EngineRequestSender {

    private EngineRequestSender() {
    }

    /** Engine API 요청을 보내고 응답 본문을 반환한다. */
    public static String send(HttpClient client, boolean tls, String host, int port, EngineCall call, Duration timeout) {
        String protocol = tls ? "https://" : "http://";
        URI uri = URI.create(protocol + host + ":" + port + call.path());
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(timeout);
        if (call.body() == null) {
            builder.method(call.method(), HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(call.method(), HttpRequest.BodyPublishers.ofString(call.body()));
        }
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String body = response.body() == null ? "" : response.body();
            int status = response.statusCode();
            if (status != 200 && status != 201 && status != 204) {
                throw new IllegalStateException(
                        "engine command failed: uri=" + uri
                                + " method=" + call.method()
                                + " status=" + status
                                + " body=" + body.trim());
            }
            return body;
        } catch (IllegalStateException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "engine command failed: uri=" + uri
                            + " method=" + call.method()
                            + " error=" + e.getClass().getSimpleName() + ": " + e.getMessage(),
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "engine command interrupted: uri=" + uri + " method=" + call.method(),
                    e);
        }
    }
}
