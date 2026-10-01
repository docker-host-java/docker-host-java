package com.github.dockerhostjava.models.image;

import com.github.dockerhostjava.models.shared.ImageRef;

public record ImageSearchResult(
        ImageRef ref,
        // tag 없음
        // pull하면 latest

        String description,
        int starCount,
        boolean official
) {
}
