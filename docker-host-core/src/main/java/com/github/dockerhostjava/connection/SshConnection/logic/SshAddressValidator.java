package com.github.dockerhostjava.connection.SshConnection.logic;

import com.github.dockerhostjava.host.SshHostObject;

public final class SshAddressValidator {

    private SshAddressValidator() {
    }

    /** user/host/port가 비었거나 범위를 벗어나면 예외를 던진다. */
    public static void requireAddress(SshHostObject host) {
        if (host.user() == null || host.user().isBlank()
                || host.host() == null || host.host().isBlank()
                || host.port() <= 0 || host.port() > 65535) {
            throw new IllegalArgumentException(
                    "ssh address is incomplete: user=" + host.user()
                            + " host=" + host.host()
                            + " port=" + host.port());
        }
    }
}
