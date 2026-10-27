package com.github.dockerhostjava.image;

import com.github.dockerhostjava.models.host.DockerHost;

import java.util.List;

public class Image {

    private DockerHost dockerHost;

    String id;      // sha256:...
    List<String> repoTags;
    List<String> repoDigests;

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Image other && id.equals(other.id);
    }
}