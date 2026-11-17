package com.github.dockerhostjava.connection.SshConnection;

import com.github.dockerhostjava.connection.SshConnection.logic.AuthenticatedSession;
import com.github.dockerhostjava.connection.SshConnection.logic.SshAddressValidator;
import com.github.dockerhostjava.connection.SshConnection.logic.SshAuthValidator;
import com.github.dockerhostjava.connection.SshConnection.logic.SshAuthenticator;
import com.github.dockerhostjava.host.SshHostObject;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;

import java.io.IOException;
import java.security.KeyPair;

public class SshConnection implements AutoCloseable {

    private SshClient client;
    private ClientSession session;

    /** SshHostObject.connect()가 내부에서 호출한다. 외부에서 직접 생성·구성하지 않는다. */
    public SshConnection() {
    }

    /** 주소와 인증 조합을 검사한 뒤 SSH 세션을 연다. */
    public void establish(SshHostObject host) {
        SshAddressValidator.requireAddress(host);
        KeyPair keyPair = SshAuthValidator.validate(host);
        AuthenticatedSession authenticated = SshAuthenticator.authenticate(host, keyPair);
        this.client = authenticated.client();
        this.session = authenticated.session();
    }

    /** SSH 세션을 닫는다. 자격 배열은 host가 소유하므로 여기서 지우지 않는다. */
    @Override
    public void close() {
        ClientSession openSession = session;
        SshClient openClient = client;
        session = null;
        client = null;
        if (openSession != null) {
            try {
                openSession.close();
            } catch (IOException ignored) {
                // The session is already being released.
            }
        }
        if (openClient != null) {
            try {
                openClient.close();
            } catch (IOException ignored) {
                // The client is already being released.
            }
        }
    }

    /** 인증이 끝난 세션이 있는지 반환한다. */
    public boolean connected() {
        return session != null && session.isAuthenticated();
    }
}
