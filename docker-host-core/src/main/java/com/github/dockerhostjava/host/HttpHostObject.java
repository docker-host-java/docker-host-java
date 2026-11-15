package com.github.dockerhostjava.host;

import com.github.dockerhostjava.connection.HttpConnection;
import com.github.dockerhostjava.connection.TlsCerts;
import com.github.dockerhostjava.models.host.DockerHost;
import com.github.dockerhostjava.models.http.HttpRequest;

import java.util.List;
import java.util.Optional;

public class HttpHostObject implements DockerHost {

    private final Object connectionLock = new Object();
    private volatile HttpConnection connection;
    private String host;
    private int port;
    private TlsCerts tlsCerts;

    /** 엔진 호스트 이름을 저장한다. */
    public HttpHostObject host(String host) {
        this.host = host;
        return this;
    }

    /** 엔진 포트를 저장한다. */
    public HttpHostObject port(int port) {
        this.port = port;
        return this;
    }

    /** TLS 인증서를 저장한다. null이면 plain http로 연결된다. */
    public HttpHostObject tlsCerts(TlsCerts tlsCerts) {
        this.tlsCerts = tlsCerts;
        return this;
    }

    /**
     * 저장된 설정으로 Docker Engine 통로를 연다. 이미 같은 TLS 설정으로 열려 있으면 그대로 두고,
     * 다른 TLS 설정으로 이미 열려 있으면 예외를 던진다.
     */
    public HttpHostObject connect() {
        open(new HttpConnection(tlsCerts != null, tlsCerts));
        return this;
    }

    /** 주소를 바꿔 다시 연결한다. 같은 TLS 설정을 유지한다. */
    public Optional<HttpConnection> reconnect(String host, int port) {
        synchronized (connectionLock) {
            HttpConnection current = requireConnection();
            if (host.equals(this.host) && port == this.port) {
                return Optional.of(current);
            }
            HttpConnection next = current.withSameTls();
            this.host = host;
            this.port = port;
            connection = null;
            return Optional.of(open(next));
        }
    }

    /** 열려 있는 HTTP 연결을 반환한다. */
    public HttpConnection connection() {
        return connection;
    }

    /** 저장된 호스트 이름을 반환한다. */
    public String host() {
        return host;
    }

    /** 저장된 포트를 반환한다. */
    public int port() {
        return port;
    }

    /** 연결이 없으면 후보를 붙이고, 이미 다른 TLS 설정으로 열려 있으면 예외를 던진다. */
    public HttpConnection open(HttpConnection candidate) {
        synchronized (connectionLock) {
            if (connection != null) {
                if (connection.tls() != candidate.tls() || connection.tlsCerts() != candidate.tlsCerts()) {
                    throw new IllegalStateException(
                            "host is already connected with other tls settings: host=" + host
                                    + " port=" + port
                                    + " tls=" + connection.tls());
                }
                return connection;
            }
            candidate.establish(this);
            connection = candidate;
            return connection;
        }
    }

    /**
     * HTTP/1.1 요청 하나를 이 호스트의 Docker Engine으로 보낸다.
     * 전송 계층 재설계 전까지는 임시 구현이다.
     */
    @Override
    public Optional<String> command(HttpRequest request) {
        throw new UnsupportedOperationException(
                "command(HttpRequest) is not wired to the transport yet: host=" + host + " port=" + port);
    }

    /** 기존 CLI 명령 목록을 받는 호출 경로. 새 command(HttpRequest)로 이행되면 제거한다. */
    public Optional<?> command(List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            throw new IllegalArgumentException("command is empty: host=" + host + " port=" + port);
        }
        return requireConnection().sendCommand(host, port, commands);
    }

    /** 연결이 없으면 먼저 연결하라는 예외를 던진다. */
    private HttpConnection requireConnection() {
        HttpConnection current = connection;
        if (current == null) {
            throw new IllegalStateException(
                    "docker engine is not connected: host=" + host + " port=" + port
                            + " call host.connect() first");
        }
        return current;
    }

    /** 이름으로 컨테이너를 찾는다. */
    @Override
    public Optional<?> findContainersByName(String name) {
        return Optional.empty();
    }

    /** 컨테이너 id로 컨테이너를 찾는다. */
    @Override
    public Optional<?> findContainersByContainerId(String containerId) {
        return Optional.empty();
    }

    /** 엔진의 이미지 목록을 조회한다. */
    @Override
    public Optional<List<?>> findImages() {
        return Optional.empty();
    }
}
