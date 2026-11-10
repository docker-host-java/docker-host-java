package com.github.dockerhostjava.models.host;

import com.github.dockerhostjava.models.http.HttpRequest;

import java.util.List;
import java.util.Optional;

public interface DockerHost {

    /**
     * 전송 수단(TCP/TLS, Unix 도메인 소켓, SSH, Named Pipe)과 무관하게
     * 같은 HTTP/1.1 요청을 그대로 받아 Docker Engine에 전달하고 응답 본문을 반환한다.
     */
    Optional<String> command(HttpRequest request);

    // 컨테이너 관련
    Optional<?> findContainersByName(String name);
    Optional<?> findContainersByContainerId(String containerId);

    // 이미지 관련
    Optional<List<?>> findImages();
}
