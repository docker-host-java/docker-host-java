package com.github.dockerhostjava.connection.SshConnection.logic;

import com.github.dockerhostjava.host.SshHostObject;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;

public final class ServerKeyVerifierFactory {

    private ServerKeyVerifierFactory() {
    }

    /** 호스트 키 정책에 맞는 서버 키 검사기를 만든다. */
    public static ServerKeyVerifier create(SshHostObject host) {
        SshHostObject.HostKeyChecking checking = host.hostKeyChecking() != null ? host.hostKeyChecking() : SshHostObject.HostKeyChecking.ACCEPT_NEW;
        if (host.knownHosts() == null) {
            if (checking == SshHostObject.HostKeyChecking.ACCEPT_NEW) {
                return (session, remote, key) -> true;
            }
            return (session, remote, key) -> {
                throw SshAuthErrors.hostKeyRejected(host.user(), host.host(), host.port(), checking, false);
            };
        }
        return new HostKeyVerifier(host, host.knownHosts(), checking);
    }
}
