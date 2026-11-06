package com.github.dockerhostjava.connection;

import com.github.dockerhostjava.host.SshHostObject;
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

    public enum Auth {
        PASSWORD,
        KEY_FILE
    }

    public enum HostKeyChecking {
        ASK,
        ACCEPT_NEW,
        YES
    }

    private static final long TIMEOUT_MILLIS = 10_000L;

    private Auth auth;
    private Path keyFilePath;
    private char[] password;
    private char[] passphrase;
    private Path knownHosts;
    private HostKeyChecking hostKeyChecking = HostKeyChecking.ACCEPT_NEW;
    private SshClient client;
    private ClientSession session;

    /** 로그인 방식을 비밀번호 또는 키 파일로 정한다. */
    public SshConnection auth(Auth auth) {
        if (auth == null) {
            throw new IllegalArgumentException("ssh auth method is missing");
        }
        this.auth = auth;
        return this;
    }

    /** SSH 비밀키 파일 경로를 저장한다. */
    public SshConnection keyFilePath(Path keyFilePath) {
        this.keyFilePath = keyFilePath;
        return this;
    }

    /** 로그인 비밀번호를 복사해 저장한다. */
    public SshConnection password(char[] password) {
        if (this.password != null) {
            Arrays.fill(this.password, '\0');
        }
        this.password = password == null ? null : password.clone();
        return this;
    }

    /** 비밀키 파일을 푸는 암호를 복사해 저장한다. */
    public SshConnection passphrase(char[] passphrase) {
        if (this.passphrase != null) {
            Arrays.fill(this.passphrase, '\0');
        }
        this.passphrase = passphrase == null ? null : passphrase.clone();
        return this;
    }

    /** 호스트 키를 기록할 known_hosts 경로를 저장한다. */
    public SshConnection knownHosts(Path knownHosts) {
        this.knownHosts = knownHosts;
        return this;
    }

    /** 처음 보는 서버 키를 어떻게 받을지 정한다. */
    public SshConnection hostKeyChecking(HostKeyChecking hostKeyChecking) {
        if (hostKeyChecking == null) {
            throw new IllegalArgumentException("ssh host key checking is missing");
        }
        this.hostKeyChecking = hostKeyChecking;
        return this;
    }

    /** 이 연결을 SSH 호스트에 연다. */
    public SshConnection connect(SshHostObject host) {
        return host.open(this);
    }

    /** 주소와 인증 조합을 검사한 뒤 SSH 세션을 연다. */
    public void establish(SshHostObject host) {
        if (host.user() == null || host.user().isBlank()
                || host.host() == null || host.host().isBlank()
                || host.port() <= 0 || host.port() > 65535) {
            throw new IllegalArgumentException(
                    "ssh address is incomplete: user=" + host.user()
                            + " host=" + host.host()
                            + " port=" + host.port());
        }
        if (auth == null) {
            throw new IllegalStateException("ssh auth method is missing: password or key file");
        }
        KeyPair keyPair = null;
        if (auth == Auth.PASSWORD) {
            if (keyFilePath != null || passphrase != null) {
                throw new IllegalStateException("ssh key file is set for password auth");
            }
            if (password == null) {
                throw new IllegalStateException("ssh password is missing");
            }
            if (password.length == 0) {
                throw new IllegalStateException("ssh password is empty");
            }
        } else {
            if (password != null) {
                throw new IllegalStateException("ssh password is set for key file auth");
            }
            if (passphrase != null && keyFilePath == null) {
                throw new IllegalStateException("ssh passphrase requires a key file");
            }
            if (keyFilePath == null) {
                throw new IllegalStateException("ssh key file is missing");
            }
            if (!Files.isRegularFile(keyFilePath)) {
                throw new IllegalStateException("ssh key file is missing: path=" + keyFilePath);
            }
            keyPair = loadKeyFile();
        }
        authenticate(host, keyPair);
    }

    /** SSH 세션을 닫고 비밀번호 배열을 지운다. */
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

    /** 같은 인증 값을 가진 새 연결을 만든다. */
    public SshConnection withSameAuth() {
        SshConnection copy = new SshConnection();
        copy.auth = auth;
        copy.keyFilePath = keyFilePath;
        copy.password = password == null ? null : password.clone();
        copy.passphrase = passphrase == null ? null : passphrase.clone();
        copy.knownHosts = knownHosts;
        copy.hostKeyChecking = hostKeyChecking;
        return copy;
    }

    /** 저장된 비밀키 파일 경로를 반환한다. */
    Path keyFilePath() {
        return keyFilePath;
    }

    /** 저장된 로그인 비밀번호를 반환한다. */
    char[] password() {
        return password;
    }

    /** 인증이 끝난 세션이 있는지 반환한다. */
    boolean connected() {
        return session != null && session.isAuthenticated();
    }

    /** 비밀키 파일을 읽어 KeyPair로 만든다. */
    private KeyPair loadKeyFile() {
        FilePasswordProvider provider = passphrase == null
                ? null
                : FilePasswordProvider.of(new String(passphrase));
        try (InputStream input = Files.newInputStream(keyFilePath)) {
            Iterable<KeyPair> keys = SecurityUtils.loadKeyPairIdentities(
                    null, new PathResource(keyFilePath), input, provider);
            if (keys == null) {
                throw new IllegalStateException("ssh key file could not be loaded: path=" + keyFilePath);
            }
            Iterator<KeyPair> iterator = keys.iterator();
            if (!iterator.hasNext()) {
                throw new IllegalStateException("ssh key file could not be loaded: path=" + keyFilePath);
            }
            return iterator.next();
        } catch (IllegalStateException e) {
            throw e;
        } catch (GeneralSecurityException | IOException e) {
            if (passphrase == null && e instanceof FailedLoginException) {
                throw new IllegalStateException(
                        "ssh key file passphrase is missing: path=" + keyFilePath, e);
            }
            throw new IllegalStateException(
                    "ssh key file could not be loaded: path=" + keyFilePath
                            + " error=" + e.getClass().getSimpleName(),
                    e);
        }
    }

    /** 키 또는 비밀번호로 SSH 인증을 끝낸다. */
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

    /** 호스트 키 정책에 맞는 서버 키 검사기를 만든다. */
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

    /** 인증 실패를 계정과 주소만 담은 예외로 바꾼다. */
    private IllegalStateException authFailed(SshHostObject host, boolean keyFileAuth, Throwable error) {
        IllegalStateException hostKey = hostKeyFailure(error);
        if (hostKey != null) {
            return hostKey;
        }
        return new IllegalStateException(
                "ssh auth failed: user=" + host.user()
                        + " host=" + host.host()
                        + " port=" + host.port()
                        + " auth=" + (keyFileAuth ? "keyFile" : "password")
                        + " error=" + error.getClass().getSimpleName(),
                error);
    }

    /** 원인 연쇄에서 호스트 키 거절 예외를 찾는다. */
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

    /** 호스트 키 거절 예외를 만든다. */
    private IllegalStateException hostKeyRejected(SshHostObject host, boolean modified) {
        return hostKeyRejected(host.user(), host.host(), host.port(), hostKeyChecking, modified);
    }

    /** 확인이 필요한 키와 거절된 키의 예외 문구를 만든다. */
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

    /** 문자 배열을 0으로 덮어 지운다. */
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

        /** known_hosts 파일과 호스트 키 정책을 보관한다. */
        private HostKeyVerifier(SshHostObject host, Path knownHosts, HostKeyChecking checking) {
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
                throw hostKeyRejected(user, hostName, port, checking, modified);
            }
            return true;
        }

        /** ACCEPT_NEW일 때만 처음 보는 키를 known_hosts에 저장한다. */
        @Override
        protected boolean acceptUnknownHostKey(
                ClientSession clientSession, SocketAddress remoteAddress, PublicKey serverKey) {
            if (checking == HostKeyChecking.ACCEPT_NEW) {
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
}
