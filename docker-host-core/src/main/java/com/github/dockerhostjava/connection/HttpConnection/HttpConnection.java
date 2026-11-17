package com.github.dockerhostjava.connection.HttpConnection;

import com.github.dockerhostjava.connection.DockerCli.DockerCli;
import com.github.dockerhostjava.connection.HttpConnection.logic.AddressValidator;
import com.github.dockerhostjava.connection.HttpConnection.logic.EnginePinger;
import com.github.dockerhostjava.connection.HttpConnection.logic.EngineRequestSender;
import com.github.dockerhostjava.connection.HttpConnection.logic.HttpClientFactory;
import com.github.dockerhostjava.connection.HttpConnection.logic.TimeoutValidator;
import com.github.dockerhostjava.connection.TlsCerts;
import com.github.dockerhostjava.host.HttpHostObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class HttpConnection {

    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    private final boolean tls;
    private final TlsCerts tlsCerts;
    private final Duration connectTimeout;
    private URI address;
    private HttpClient client;

    /** HttpHostObject.connect()가 내부에서 호출한다. 외부에서 직접 생성·구성하지 않는다. */
    public HttpConnection(boolean tls, TlsCerts tlsCerts, Duration connectTimeout) {
        this.tls = tls;
        this.tlsCerts = tlsCerts;
        this.connectTimeout = TimeoutValidator.requirePositive(connectTimeout);
    }

    /** 기본 타임아웃으로 생성한다. */
    public HttpConnection(boolean tls, TlsCerts tlsCerts) {
        this(tls, tlsCerts, DEFAULT_TIMEOUT);
    }

    /** TLS 사용 여부. */
    public boolean tls() {
        return tls;
    }

    /** 저장된 TLS 인증서 묶음(없으면 null). */
    public TlsCerts tlsCerts() {
        return tlsCerts;
    }

    /** 같은 TLS 설정과 제한 시간을 가진 연결 전 객체를 만든다. */
    public HttpConnection withSameTls() {
        return new HttpConnection(tls, tlsCerts, connectTimeout);
    }

    /** 주소를 만들고 GET /_ping으로 엔진 연결을 확인한다. */
    public void establish(HttpHostObject host) {
        AddressValidator.requireAddress(host.host(), host.port());
        String protocol = tls ? "https://" : "http://";
        this.address = URI.create(protocol + host.host() + ":" + host.port());
        this.client = HttpClientFactory.create(tls, tlsCerts, address, connectTimeout);
        EnginePinger.ping(client, address, connectTimeout);
    }

    /** CLI 한 줄마다 엔진 API를 호출하고 응답 본문 목록을 반환한다. */
    public Optional<List<String>> sendCommand(String host, int port, List<String> commands) {
        if (client == null) {
            throw new IllegalStateException(
                    "docker engine is not connected: host=" + host + " port=" + port);
        }
        AddressValidator.requireAddress(host, port);
        if (commands == null || commands.isEmpty()) {
            throw new IllegalArgumentException("command is empty: host=" + host + " port=" + port);
        }
        List<String> bodies = new ArrayList<>();
        for (String command : commands) {
            bodies.add(EngineRequestSender.send(client, tls, host, port, DockerCli.translate(command), connectTimeout));
        }
        return Optional.of(bodies);
    }
}
