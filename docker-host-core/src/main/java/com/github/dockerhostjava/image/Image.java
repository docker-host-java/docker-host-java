package com.github.dockerhostjava.image;

import com.github.dockerhostjava.models.host.DockerHost;

import java.util.List;

public class Image {

    private DockerHost dockerHost;

    String id;      // sha256:...
    List<String> repoTags;
    List<String> repoDigests;

    // 스냅샷 정보는 일일히 요청 -> 불일치 방지

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Image other && id.equals(other.id);
    }
}