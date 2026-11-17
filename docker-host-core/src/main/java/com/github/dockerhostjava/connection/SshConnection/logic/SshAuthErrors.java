package com.github.dockerhostjava.connection.SshConnection.logic;

import com.github.dockerhostjava.host.SshHostObject;

public final class SshAuthErrors {

    private SshAuthErrors() {
    }

    /** 인증 실패를 계정과 주소만 담은 예외로 바꾼다. */
    public static IllegalStateException authFailed(SshHostObject host, boolean keyFileAuth, Throwable error) {
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
    public static IllegalStateException hostKeyFailure(Throwable error) {
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

    /** 확인이 필요한 키와 거절된 키의 예외 문구를 만든다. */
    public static IllegalStateException hostKeyRejected(
            String user, String host, int port, SshHostObject.HostKeyChecking checking, boolean modified) {
        if (checking == SshHostObject.HostKeyChecking.ASK && !modified) {
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
}
