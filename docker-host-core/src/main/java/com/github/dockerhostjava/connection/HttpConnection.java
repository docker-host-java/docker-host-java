package com.github.dockerhostjava.connection;

import com.github.dockerhostjava.host.HttpHostObject;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.github.dockerhostjava.connection.DockerCli.EngineCall;

public class HttpConnection {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    private final boolean tls;
    private final TlsCerts tlsCerts;
    private final Duration connectTimeout;
    private URI address;
    private HttpClient client;

    /** TLS 여부와 인증서는 팩토리에서만 정한다. */
    private HttpConnection(boolean tls, TlsCerts tlsCerts, Duration connectTimeout) {
        this.tls = tls;
        this.tlsCerts = tlsCerts;
        this.connectTimeout = connectTimeout;
    }

    /** 호스트에 http로 연결한다. */
    public static HttpConnection connectPlain(HttpHostObject host) {
        return connectPlain(host, DEFAULT_TIMEOUT);
    }

    /** 호스트에 http로 연결하고 제한 시간을 정한다. */
    public static HttpConnection connectPlain(HttpHostObject host, Duration connectTimeout) {
        return new HttpConnection(false, null, timeout(connectTimeout)).connect(host);
    }

    /** 호스트에 인증서를 붙여 https로 연결한다. */
    public static HttpConnection connectTls(HttpHostObject host, TlsCerts tlsCerts) {
        return connectTls(host, tlsCerts, DEFAULT_TIMEOUT);
    }

    /** 호스트에 인증서를 붙여 https로 연결하고 제한 시간을 정한다. */
    public static HttpConnection connectTls(HttpHostObject host, TlsCerts tlsCerts, Duration connectTimeout) {
        if (tlsCerts == null) {
            throw new IllegalArgumentException(
                    "tls certs are missing: host=" + host.host() + " port=" + host.port()
                            + " tls requires ca.pem, cert.pem, and key.pem");
        }
        return new HttpConnection(true, tlsCerts, timeout(connectTimeout)).connect(host);
    }

    /** 같은 TLS 설정과 제한 시간을 가진 연결 전 객체를 만든다. */
    public HttpConnection withSameTls() {
        return new HttpConnection(tls, tlsCerts, connectTimeout);
    }

    /** 이 연결을 호스트에 열고, 이미 다른 TLS 설정으로 연결돼 있으면 예외를 던진다. */
    private HttpConnection connect(HttpHostObject host) {
        HttpConnection opened = host.open(this);
        if (opened.tls != tls || opened.tlsCerts != tlsCerts) {
            throw new IllegalStateException(
                    "host is already connected with other tls settings: host=" + host.host()
                            + " port=" + host.port()
                            + " tls=" + opened.tls);
        }
        return opened;
    }

    /** 제한 시간이 비었거나 0 이하이면 예외를 던진다. */
    private static Duration timeout(Duration connectTimeout) {
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalArgumentException("connect timeout must be positive: timeout=" + connectTimeout);
        }
        return connectTimeout;
    }

    /** 주소를 만들고 GET /_ping으로 엔진 연결을 확인한다. */
    public void establish(HttpHostObject host) {
        if (host.host() == null || host.host().isBlank() || host.port() <= 0 || host.port() > 65535) {
            throw new IllegalArgumentException(
                    "host address is incomplete: host=" + host.host() + " port=" + host.port());
        }
        String protocol = tls ? "https://" : "http://";
        this.address = URI.create(protocol + host.host() + ":" + host.port());
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(connectTimeout);
        if (tls) {
            builder.sslContext(sslContext());
        }
        this.client = builder.build();
        ping(connectTimeout);
    }

    /** CLI 한 줄마다 엔진 API를 호출하고 응답 본문 목록을 반환한다. */
    public Optional<List<String>> sendCommand(String host, int port, List<String> commands) {
        if (client == null) {
            throw new IllegalStateException(
                    "docker engine is not connected: host=" + host + " port=" + port);
        }
        if (host == null || host.isBlank() || port <= 0 || port > 65535) {
            throw new IllegalArgumentException(
                    "host address is incomplete: host=" + host + " port=" + port);
        }
        if (commands == null || commands.isEmpty()) {
            throw new IllegalArgumentException("command is empty: host=" + host + " port=" + port);
        }
        List<String> bodies = new ArrayList<>();
        for (String command : commands) {
            bodies.add(send(host, port, DockerCli.translate(command)));
        }
        return Optional.of(bodies);
    }

    /** Engine API 요청을 보내고 응답 본문을 반환한다. */
    private String send(String host, int port, EngineCall call) {
        String protocol = tls ? "https://" : "http://";
        URI uri = URI.create(protocol + host + ":" + port + call.path());
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(connectTimeout);
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

    /** 저장된 PEM으로 SSLContext를 만든다. */
    private SSLContext sslContext() {
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

    /** GET /_ping 응답이 OK인지 확인한다. */
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

}
