package com.github.dockerhostjava.api.models.container;

public record ExecResult(
    Integer exitCode,
    String stdout,
    String stderr
) {
}
