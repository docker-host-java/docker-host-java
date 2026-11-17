package com.github.dockerhostjava.connection.SshConnection.logic;

import com.github.dockerhostjava.host.SshHostObject;
import org.apache.sshd.client.config.hosts.KnownHostEntry;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;

import java.net.SocketAddress;
import java.nio.file.Path;
import java.security.PublicKey;

public final class HostKeyVerifier extends KnownHostsServerKeyVerifier {

    private final SshHostObject.HostKeyChecking checking;
    private final String user;
    private final String hostName;
    private final int port;
    private boolean modified;

    /** known_hosts 파일과 호스트 키 정책을 보관한다. */
    public HostKeyVerifier(SshHostObject host, Path knownHosts, SshHostObject.HostKeyChecking checking) {
        super((session, remote, key) -> true, knownHosts);
        this.checking = checking;
        this.user = host.user();
        this.hostName = host.host();
        this.port = host.port();
    }

    /** 서버 키가 정책에 맞지 않으면 예외를 던진다. */
    @Override
    public boolean verifyServerKey(ClientSession clientSession, SocketAddress remoteAddress, PublicKey serverKey) {
        modified = false;
        if (!super.verifyServerKey(clientSession, remoteAddress, serverKey)) {
            throw SshAuthErrors.hostKeyRejected(user, hostName, port, checking, modified);
        }
        return true;
    }

    /** ACCEPT_NEW일 때만 처음 보는 키를 known_hosts에 저장한다. */
    @Override
    protected boolean acceptUnknownHostKey(
            ClientSession clientSession, SocketAddress remoteAddress, PublicKey serverKey) {
        if (checking == SshHostObject.HostKeyChecking.ACCEPT_NEW) {
            return super.acceptUnknownHostKey(clientSession, remoteAddress, serverKey);
        }
        return false;
    }

    /** 저장된 키와 다른 서버 키는 거절한다. */
    @Override
    public boolean acceptModifiedServerKey(
            ClientSession clientSession,
            SocketAddress remoteAddress,
            KnownHostEntry entry,
            PublicKey expected,
            PublicKey actual) {
        modified = true;
        return false;
    }
}
