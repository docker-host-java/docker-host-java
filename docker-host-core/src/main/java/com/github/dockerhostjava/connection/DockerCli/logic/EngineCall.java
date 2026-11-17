package com.github.dockerhostjava.connection.DockerCli.logic;

/** Engine API 호출의 메서드, 경로, 본문을 담는다. */
public record EngineCall(String method, String path, String body) {

    /** 본문 없는 GET 호출을 만든다. */
    public static EngineCall get(String path) {
        return new EngineCall("GET", path, null);
    }

    /** 본문 없는 POST 호출을 만든다. */
    public static EngineCall post(String path) {
        return new EngineCall("POST", path, null);
    }

    /** 본문 없는 DELETE 호출을 만든다. */
    public static EngineCall delete(String path) {
        return new EngineCall("DELETE", path, null);
    }
}
