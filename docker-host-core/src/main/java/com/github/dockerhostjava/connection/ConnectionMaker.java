package com.github.dockerhostjava.connection;

import java.time.Duration;

public class ConnectionMaker {

    private String host;
    private int port;
    private boolean tlsVerify;
    private TlsCerts tlsCerts;
    private Duration connectTimeout = Duration.ofSeconds(3);


    // builder pattern

    public ConnectionMaker host(String host) {
        this.host = host;
        return this;
    }

    public ConnectionMaker port(int port) {
        this.port = port;
        return this;
    }

    public ConnectionMaker tlsVerify(boolean tlsVerify) {
        this.tlsVerify = tlsVerify;
        return this;
    }

    public ConnectionMaker tlsCerts(TlsCerts tlsCerts) {
        this.tlsCerts = tlsCerts;
        return this;
    }

    public ConnectionMaker connectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    String connectAddress() {
        String protocol = tlsVerify ? "https://" : "http://";
        return protocol + host + ":" + port;
    }

    TlsCerts tlsCerts() {
        return tlsCerts;
    }

    // to readable EngineHttp
    Duration connectTimeout() {
        return connectTimeout;
    }
}
