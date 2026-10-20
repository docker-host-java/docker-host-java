package com.github.dockerhostjava.connection;

import com.github.dockerhostjava.models.host.DockerHost;

import java.util.List;
import java.util.Optional;

public class HostObject implements DockerHost {

    private final Object connectionLock = new Object();
    private volatile Connection connection;
    private String host;
    private int port;

    public HostObject host(String host) {
        this.host = host;
        return this;
    }

    public HostObject port(int port) {
        this.port = port;
        return this;
    }

    public Connection connection() {
        return connection;
    }

    String host() {
        return host;
    }

    int port() {
        return port;
    }

    Connection open(Connection candidate) {
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
    public Optional<Connection> makeConnection(String host, int port) {
        synchronized (connectionLock) {
            if (connection != null && host.equals(this.host) && port == this.port) {
                return Optional.of(connection);
            }
            this.host = host;
            this.port = port;
            connection = null;
            return Optional.of(new Connection().connect(this));
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
