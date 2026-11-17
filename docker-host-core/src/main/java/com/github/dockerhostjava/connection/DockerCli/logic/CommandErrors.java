package com.github.dockerhostjava.connection.DockerCli.logic;

public final class CommandErrors {

    private CommandErrors() {
    }

    /** 지원하지 않는 명령 예외를 만든다. */
    public static IllegalArgumentException unsupported(String command) {
        return new IllegalArgumentException("command is unsupported: " + command);
    }

    /** 지원하지 않는 플래그 예외를 만든다. */
    public static IllegalArgumentException unsupportedFlag(String flag) {
        return new IllegalArgumentException("command flag is unsupported: " + flag);
    }
}
