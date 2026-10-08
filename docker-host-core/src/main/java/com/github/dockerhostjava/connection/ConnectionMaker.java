package com.github.dockerhostjava.connection;

import java.nio.file.Path;
import java.time.Duration;

public class ConnectionMaker {

    private String host;
    private int port;
    private boolean tlsVerify;
    private Path certPath;
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

    public ConnectionMaker certPath(Path certPath) {
        this.certPath = certPath;
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

    // to readable EngineHttp
    Path certPath() {
        return certPath;
    }

    // to readable EngineHttp
    Duration connectTimeout() {
        return connectTimeout;
    }
}
