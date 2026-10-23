package com.github.dockerhostjava.connection;

import com.github.dockerhostjava.models.host.DockerHost;

import java.util.List;
import java.util.Optional;

public class HttpHostObject implements DockerHost {

    private final Object connectionLock = new Object();
    private volatile HttpConnection connection;
    private String host;
    private int port;

    public HttpHostObject host(String host) {
        this.host = host;
        return this;
    }

    public HttpHostObject port(int port) {
        this.port = port;
        return this;
    }

    public HttpConnection connection() {
        return connection;
    }

    String host() {
        return host;
    }

    int port() {
        return port;
    }

    HttpConnection open(HttpConnection candidate) {
        synchronized (connectionLock) {
            if (connection != null) {
                return connection;
            }
            candidate.establish(this);
            connection = candidate;
            return connection;
        }
    }

    @Override
    public Optional<HttpConnection> makeConnection(String host, int port) {
        synchronized (connectionLock) {
            if (connection != null && host.equals(this.host) && port == this.port) {
                return Optional.of(connection);
            }
            this.host = host;
            this.port = port;
            connection = null;
            return Optional.of(new HttpConnection().connect(this));
        }
    }

    @Override
    public Optional<?> findContainersByName(String name) {
        return Optional.empty();
    }

    @Override
    public Optional<?> findContainersByContainerId(String containerId) {
        return Optional.empty();
    }

    @Override
    public Optional<List<?>> findImages() {
        return Optional.empty();
    }
}
