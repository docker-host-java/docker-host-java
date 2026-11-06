package com.github.dockerhostjava.models.image;

import com.github.dockerhostjava.models.shared.ImageRef;

import java.time.Instant;
import java.util.Set;

public record ImageHistory(
        String id,           // 로컬에 없는 중간 레이어는 "<missing>"
        Instant createdAt,
        String createdBy,    // 레이어를 만든 Dockerfile 명령
        Set<ImageRef> tags,
        long size,
        String comment
) {
}