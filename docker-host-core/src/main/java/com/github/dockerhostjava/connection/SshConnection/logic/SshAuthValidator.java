package com.github.dockerhostjava.connection.SshConnection.logic;

import com.github.dockerhostjava.host.SshHostObject;

import java.nio.file.Files;
import java.security.KeyPair;

public final class SshAuthValidator {

    private SshAuthValidator() {
    }

    /** 인증 상태를 점검하고 키 파일 인증이면 KeyPair를 만든다. 비밀번호 인증이면 null을 반환한다. */
    public static KeyPair validate(SshHostObject host) {
        SshHostObject.Auth auth = host.auth();
        if (auth == null) {
            throw new IllegalStateException("ssh auth method is missing: password or key file");
        }
        if (auth == SshHostObject.Auth.PASSWORD) {
            if (host.keyFilePath() != null || host.passphrase() != null) {
                throw new IllegalStateException("ssh key file is set for password auth");
            }
            if (host.password() == null) {
                throw new IllegalStateException("ssh password is missing");
            }
            if (host.password().length == 0) {
                throw new IllegalStateException("ssh password is empty");
            }
            return null;
        }
        if (host.password() != null) {
            throw new IllegalStateException("ssh password is set for key file auth");
        }
        if (host.passphrase() != null && host.keyFilePath() == null) {
            throw new IllegalStateException("ssh passphrase requires a key file");
        }
        if (host.keyFilePath() == null) {
            throw new IllegalStateException("ssh key file is missing");
        }
        if (!Files.isRegularFile(host.keyFilePath())) {
            throw new IllegalStateException("ssh key file is missing: path=" + host.keyFilePath());
        }
        return KeyFileLoader.load(host.keyFilePath(), host.passphrase());
    }
}
