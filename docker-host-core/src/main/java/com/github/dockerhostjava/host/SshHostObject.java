package com.github.dockerhostjava.host;

import com.github.dockerhostjava.connection.SshConnection;
import com.github.dockerhostjava.models.host.DockerHost;
import com.github.dockerhostjava.models.http.HttpRequest;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class SshHostObject implements DockerHost {

    private final Object connectionLock = new Object();
    private volatile SshConnection connection;
    private String user;
    private String host;
    private int port = 22;
    private SshConnection.Auth auth;
    private char[] password;
    private Path keyFilePath;
    private char[] passphrase;
    private Path knownHosts;
    private SshConnection.HostKeyChecking hostKeyChecking;

    /** SSH 로그인 계정을 저장한다. */
    public SshHostObject user(String user) {
        this.user = user;
        return this;
    }

    /** SSH 호스트 이름을 저장한다. */
    public SshHostObject host(String host) {
        this.host = host;
        return this;
    }

    /** SSH 포트를 저장한다. */
    public SshHostObject port(int port) {
        this.port = port;
        return this;
    }

    /** 로그인 방식을 비밀번호 또는 키 파일로 정한다. */
    public SshHostObject auth(SshConnection.Auth auth) {
        if (auth == null) {
            throw new IllegalArgumentException("ssh auth method is missing");
        }
        this.auth = auth;
        return this;
    }

    /** 로그인 비밀번호를 복사해 저장한다. */
    public SshHostObject password(char[] password) {
        if (this.password != null) {
            Arrays.fill(this.password, '\0');
        }
        this.password = password == null ? null : password.clone();
        return this;
    }

    /** SSH 비밀키 파일 경로를 저장한다. */
    public SshHostObject keyFilePath(Path keyFilePath) {
        this.keyFilePath = keyFilePath;
        return this;
    }

    /** 비밀키 파일을 푸는 암호를 복사해 저장한다. */
    public SshHostObject passphrase(char[] passphrase) {
        if (this.passphrase != null) {
            Arrays.fill(this.passphrase, '\0');
        }
        this.passphrase = passphrase == null ? null : passphrase.clone();
        return this;
    }

    /** 호스트 키를 기록할 known_hosts 경로를 저장한다. */
    public SshHostObject knownHosts(Path knownHosts) {
        this.knownHosts = knownHosts;
        return this;
    }

    /** 처음 보는 서버 키를 어떻게 받을지 정한다. */
    public SshHostObject hostKeyChecking(SshConnection.HostKeyChecking hostKeyChecking) {
        if (hostKeyChecking == null) {
            throw new IllegalArgumentException("ssh host key checking is missing");
        }
        this.hostKeyChecking = hostKeyChecking;
        return this;
    }

    /** 저장된 설정으로 SSH 통로를 연다. 이미 열려 있으면 그대로 반환한다. */
    public SshHostObject connect() {
        if (connection != null) {
            return this;
        }
        SshConnection candidate = new SshConnection();
        if (auth != null) {
            candidate.auth(auth);
        }
        if (password != null) {
            candidate.password(password);
        }
        if (keyFilePath != null) {
            candidate.keyFilePath(keyFilePath);
        }
        if (passphrase != null) {
            candidate.passphrase(passphrase);
        }
        if (knownHosts != null) {
            candidate.knownHosts(knownHosts);
        }
        if (hostKeyChecking != null) {
            candidate.hostKeyChecking(hostKeyChecking);
        }
        candidate.connect(this);
        return this;
    }

    /** 주소를 바꿔 다시 연결한다. 인증 설정은 유지한다. */
    public Optional<SshConnection> reconnect(String host, int port) {
        return makeConnection(host, port);
    }

    /** 열려 있는 SSH 연결을 반환한다. */
    public SshConnection connection() {
        return connection;
    }

    /** 저장된 로그인 계정을 반환한다. */
    public String user() {
        return user;
    }

    /** 저장된 호스트 이름을 반환한다. */
    public String host() {
        return host;
    }

    /** 저장된 포트를 반환한다. */
    public int port() {
        return port;
    }

    /** 연결이 없으면 후보를 붙이고, 있으면 기존 연결을 반환한다. */
    public SshConnection open(SshConnection candidate) {
        synchronized (connectionLock) {
            if (connection != null) {
                return connection;
            }
            candidate.establish(this);
            connection = candidate;
            return connection;
        }
    }

    /** 같은 주소면 기존 연결을 반환하고, 다르면 인증을 유지한 채 다시 연결한다. */
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

    /**
     * HTTP/1.1 요청 하나를 SSH 터널을 통해 원격 Docker Engine으로 보낸다.
     * 전송 계층 재설계 전까지는 임시 구현이다.
     */
    @Override
    public Optional<String> command(HttpRequest request) {
        throw new UnsupportedOperationException(
                "command(HttpRequest) is not wired to the transport yet: user=" + user
                        + " host=" + host + " port=" + port);
    }

    /** 기존 CLI 명령 목록을 받는 호출 경로. 새 command(HttpRequest)로 이행되면 제거한다. */
    public Optional<?> command(List<String> commands) {
        return Optional.empty();
    }

    /** 이름으로 컨테이너를 찾는다. */
    @Override
    public Optional<?> findContainersByName(String name) {
        return Optional.empty();
    }

    /** 컨테이너 id로 컨테이너를 찾는다. */
    @Override
    public Optional<?> findContainersByContainerId(String containerId) {
        return Optional.empty();
    }

    /** 엔진의 이미지 목록을 조회한다. */
    @Override
    public Optional<List<?>> findImages() {
        return Optional.empty();
    }
}
