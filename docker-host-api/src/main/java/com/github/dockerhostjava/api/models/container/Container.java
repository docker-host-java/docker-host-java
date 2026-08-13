package com.github.dockerhostjava.api.models.container;

import java.util.Optional;

public interface Container {

    void start();
    void stop();
    void kill();
    void restart();
    void pause();
    void unpause();
    void rename(String name);

    Optional<ExecResult> exec(String... command);

    // get
    String getId();
    ContainerStatus getStatus();
    Optional<Integer> getExitCode();
    <T> T getHost();
    <T> T getImage();

}
