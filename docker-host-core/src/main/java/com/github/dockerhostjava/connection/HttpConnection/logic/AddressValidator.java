package com.github.dockerhostjava.connection.HttpConnection.logic;

public final class AddressValidator {

    private AddressValidator() {
    }

    /** 호스트/포트가 비었거나 범위를 벗어나면 예외를 던진다. */
    public static void requireAddress(String host, int port) {
        if (host == null || host.isBlank() || port <= 0 || port > 65535) {
            throw new IllegalArgumentException(
                    "host address is incomplete: host=" + host + " port=" + port);
        }
    }
}
