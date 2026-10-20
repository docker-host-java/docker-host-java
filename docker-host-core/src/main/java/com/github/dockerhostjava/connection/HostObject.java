package com.github.dockerhostjava.connection;

import com.github.dockerhostjava.models.host.DockerHost;

import java.util.List;
import java.util.Optional;

public class HostObject implements DockerHost {

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

    String host() {
        return host;
    }

    int port() {
        return port;
    }

    @Override
    public Optional<Connection> makeConnection(String host, int port) {
        this.host = host;
        this.port = port;
        try {
            return Optional.of(new Connection().connect(this));
        } catch (IllegalStateException e) {
            return Optional.empty();
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
