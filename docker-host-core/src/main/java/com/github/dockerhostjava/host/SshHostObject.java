package com.github.dockerhostjava.connection;

import com.github.dockerhostjava.models.host.DockerHost;

import java.util.List;
import java.util.Optional;

public class SshHostObject implements DockerHost {

    private final Object connectionLock = new Object();
    private volatile SshConnection connection;
    private String user;
    private String host;
    private int port = 22;

    public TlsCerts.SshHostObject user(String user) {
        this.user = user;
        return this;
    }

    public TlsCerts.SshHostObject host(String host) {
        this.host = host;
        return this;
    }

    public TlsCerts.SshHostObject port(int port) {
        this.port = port;
        return this;
    }

    public SshConnection connection() {
        return connection;
    }

    String user() {
        return user;
    }

    String host() {
        return host;
    }

    int port() {
        return port;
    }

    SshConnection open(SshConnection candidate) {
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
    public Optional<SshConnection> makeConnection(String host, int port) {
        synchronized (connectionLock) {
            if (connection != null && host.equals(this.host) && port == this.port) {
                return Optional.of(connection);
            }
            SshConnection previous = connection;
            SshConnection next = previous == null ? new SshConnection() : previous.withSameAuth();
            this.host = host;
            this.port = port;
            connection = null;
            if (previous != null) {
                previous.close();
            }
            return Optional.of(next.connect(this));
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
