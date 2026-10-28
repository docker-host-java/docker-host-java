package com.github.dockerhostjava.connection;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.config.hosts.KnownHostEntry;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.common.util.io.resource.PathResource;
import org.apache.sshd.common.util.security.SecurityUtils;

import javax.security.auth.login.FailedLoginException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

public class SshConnection implements AutoCloseable {

    public enum HostKeyChecking {
        ASK,
        ACCEPT_NEW,
        YES
    }

    private static final long TIMEOUT_MILLIS = 10_000L;

    private Path identity;
    private char[] password;
    private char[] passphrase;
    private Path knownHosts;
    private HostKeyChecking hostKeyChecking = HostKeyChecking.ACCEPT_NEW;
    private SshClient client;
    private ClientSession session;

    public SshConnection identity(Path identity) {
        this.identity = identity;
        return this;
    }

    public SshConnection password(char[] password) {
        if (this.password != null) {
            Arrays.fill(this.password, '\0');
        }
        this.password = password == null ? null : password.clone();
        return this;
    }

    public SshConnection passphrase(char[] passphrase) {
        if (this.passphrase != null) {
            Arrays.fill(this.passphrase, '\0');
        }
        this.passphrase = passphrase == null ? null : passphrase.clone();
        return this;
    }

    public SshConnection knownHosts(Path knownHosts) {
        this.knownHosts = knownHosts;
        return this;
    }

    public SshConnection hostKeyChecking(HostKeyChecking hostKeyChecking) {
        if (hostKeyChecking == null) {
            throw new IllegalArgumentException("ssh host key checking is missing");
        }
        this.hostKeyChecking = hostKeyChecking;
        return this;
    }

    public SshConnection connect(SshHostObject host) {
        return host.open(this);
    }

    void establish(SshHostObject host) {
        if (host.user() == null || host.user().isBlank()
                || host.host() == null || host.host().isBlank()
                || host.port() <= 0 || host.port() > 65535) {
            throw new IllegalArgumentException(
                    "ssh address is incomplete: user=" + host.user()
                            + " host=" + host.host()
                            + " port=" + host.port());
        }
        if (identity != null && password != null) {
            throw new IllegalStateException("ssh auth is ambiguous: identity and password are both set");
        }
        if (passphrase != null && identity == null) {
            throw new IllegalStateException("ssh passphrase requires an identity");
        }
        if (identity == null && password == null) {
            throw new IllegalStateException("ssh auth is missing: identity or password is required");
        }
        KeyPair keyPair = null;
        if (identity != null) {
            if (!Files.isRegularFile(identity)) {
                throw new IllegalStateException("ssh identity file is missing: path=" + identity);
            }
            keyPair = loadIdentity();
        } else if (password.length == 0) {
            throw new IllegalStateException("ssh password is empty");
        }
        authenticate(host, keyPair);
    }

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
        wipe(password);
        password = null;
        wipe(passphrase);
        passphrase = null;
    }

    SshConnection withSameAuth() {
        SshConnection copy = new SshConnection();
        copy.identity = identity;
        copy.password = password == null ? null : password.clone();
        copy.passphrase = passphrase == null ? null : passphrase.clone();
        copy.knownHosts = knownHosts;
        copy.hostKeyChecking = hostKeyChecking;
        return copy;
    }

    Path identity() {
        return identity;
    }

    char[] password() {
        return password;
    }

    boolean connected() {
        return session != null && session.isAuthenticated();
    }

    private KeyPair loadIdentity() {
        FilePasswordProvider provider = passphrase == null
                ? null
                : FilePasswordProvider.of(new String(passphrase));
        try (InputStream input = Files.newInputStream(identity)) {
            Iterable<KeyPair> keys = SecurityUtils.loadKeyPairIdentities(
                    null, new PathResource(identity), input, provider);
            if (keys == null) {
                throw new IllegalStateException("ssh identity could not be loaded: path=" + identity);
            }
            Iterator<KeyPair> iterator = keys.iterator();
            if (!iterator.hasNext()) {
                throw new IllegalStateException("ssh identity could not be loaded: path=" + identity);
            }
            return iterator.next();
        } catch (IllegalStateException e) {
            throw e;
        } catch (GeneralSecurityException | IOException e) {
            if (passphrase == null && e instanceof FailedLoginException) {
                throw new IllegalStateException(
                        "ssh identity passphrase is missing: path=" + identity, e);
            }
            throw new IllegalStateException(
                    "ssh identity could not be loaded: path=" + identity
                            + " error=" + e.getClass().getSimpleName(),
                    e);
        }
    }

    private void authenticate(SshHostObject host, KeyPair keyPair) {
        SshClient opening = SshClient.setUpDefaultClient();
        opening.setKeyIdentityProvider(KeyIdentityProvider.EMPTY_KEYS_PROVIDER);
        opening.setServerKeyVerifier(serverKeyVerifier(host));
        opening.start();
        ClientSession openingSession = null;
        try {
            openingSession = opening.connect(host.user(), host.host(), host.port())
                    .verify(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                    .getSession();
            if (keyPair != null) {
                openingSession.addPublicKeyIdentity(keyPair);
            } else {
                openingSession.addPasswordIdentity(new String(password));
            }
            openingSession.auth().verify(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            this.client = opening;
            this.session = openingSession;
        } catch (RuntimeException | IOException e) {
            throw authFailed(host, keyPair != null, e);
        } finally {
            if (this.session != openingSession) {
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

    private ServerKeyVerifier serverKeyVerifier(SshHostObject host) {
        if (knownHosts == null) {
            if (hostKeyChecking == HostKeyChecking.ACCEPT_NEW) {
                return (session, remote, key) -> true;
            }
            return (session, remote, key) -> {
                throw hostKeyRejected(host, false);
            };
        }
        return new HostKeyVerifier(host, knownHosts, hostKeyChecking);
    }

    private IllegalStateException authFailed(SshHostObject host, boolean identityAuth, Throwable error) {
        IllegalStateException hostKey = hostKeyFailure(error);
        if (hostKey != null) {
            return hostKey;
        }
        return new IllegalStateException(
                "ssh auth failed: user=" + host.user()
                        + " host=" + host.host()
                        + " port=" + host.port()
                        + " auth=" + (identityAuth ? "identity" : "password")
                        + " error=" + error.getClass().getSimpleName(),
                error);
    }

    private static IllegalStateException hostKeyFailure(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 20; depth++) {
            if (current instanceof IllegalStateException
                    && current.getMessage() != null
                    && current.getMessage().startsWith("ssh host key")) {
                return (IllegalStateException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private IllegalStateException hostKeyRejected(SshHostObject host, boolean modified) {
        return hostKeyRejected(host.user(), host.host(), host.port(), hostKeyChecking, modified);
    }

    private static IllegalStateException hostKeyRejected(
            String user, String host, int port, HostKeyChecking checking, boolean modified) {
        if (checking == HostKeyChecking.ASK && !modified) {
            return new IllegalStateException(
                    "ssh host key confirmation is required: user=" + user
                            + " host=" + host
                            + " port=" + port
                            + " checking=ASK");
        }
        return new IllegalStateException(
                "ssh host key was rejected: user=" + user
                        + " host=" + host
                        + " port=" + port
                        + " checking=" + checking);
    }

    private static void wipe(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }

    private static final class HostKeyVerifier extends KnownHostsServerKeyVerifier {

        private final HostKeyChecking checking;
        private final String user;
        private final String hostName;
        private final int port;
        private boolean modified;

        private HostKeyVerifier(SshHostObject host, Path knownHosts, HostKeyChecking checking) {
            super((session, remote, key) -> true, knownHosts);
            this.checking = checking;
            this.user = host.user();
            this.hostName = host.host();
            this.port = host.port();
        }

        @Override
        public boolean verifyServerKey(ClientSession clientSession, SocketAddress remoteAddress, PublicKey serverKey) {
            modified = false;
            if (!super.verifyServerKey(clientSession, remoteAddress, serverKey)) {
                throw hostKeyRejected(user, hostName, port, checking, modified);
            }
            return true;
        }

        @Override
        protected boolean acceptUnknownHostKey(
                ClientSession clientSession, SocketAddress remoteAddress, PublicKey serverKey) {
            if (checking == HostKeyChecking.ACCEPT_NEW) {
                return super.acceptUnknownHostKey(clientSession, remoteAddress, serverKey);
            }
            return false;
        }

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
}
