package com.github.dockerhostjava.connection.SshConnection.logic;

import com.github.dockerhostjava.host.SshHostObject;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;

import java.io.IOException;
import java.security.KeyPair;
import java.util.concurrent.TimeUnit;

public final class SshAuthenticator {

    private static final long TIMEOUT_MILLIS = 10_000L;

    private SshAuthenticator() {
    }

    /** 키 또는 비밀번호로 SSH 인증을 끝낸다. */
    public static AuthenticatedSession authenticate(SshHostObject host, KeyPair keyPair) {
        SshClient opening = SshClient.setUpDefaultClient();
        opening.setKeyIdentityProvider(KeyIdentityProvider.EMPTY_KEYS_PROVIDER);
        opening.setServerKeyVerifier(ServerKeyVerifierFactory.create(host));
        opening.start();
        ClientSession openingSession = null;
        boolean authenticated = false;
        try {
            openingSession = opening.connect(host.user(), host.host(), host.port())
                    .verify(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                    .getSession();
            if (keyPair != null) {
                openingSession.addPublicKeyIdentity(keyPair);
            } else {
                openingSession.addPasswordIdentity(new String(host.password()));
            }
            openingSession.auth().verify(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            authenticated = true;
            return new AuthenticatedSession(opening, openingSession);
        } catch (RuntimeException | IOException e) {
            throw SshAuthErrors.authFailed(host, keyPair != null, e);
        } finally {
            if (!authenticated) {
                if (openingSession != null) {
                    try {
                        openingSession.close();
                    } catch (IOException ignored) {
                        // The failed session is already being released.
                    }
                }
                try {
                    opening.close();
                } catch (IOException ignored) {
                    // The failed client is already being released.
                }
            }
        }
    }
}
