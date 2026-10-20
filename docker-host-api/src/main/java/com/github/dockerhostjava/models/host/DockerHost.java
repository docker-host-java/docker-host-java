package com.github.dockerhostjava.models.host;

import java.util.List;
import java.util.Optional;

public interface DockerHost {

    // 호스트 연결
    Optional<?> makeConnection(String host, int port);

    // 컨테이너 관련
    Optional<?> findContainersByName(String name);
    Optional<?> findContainersByContainerId(String containerId);

    // 이미지 관련
    Optional<List<?>> findImages();

}
