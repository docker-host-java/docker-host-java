package com.github.dockerhostjava.models.shared;

public final class ImageRef {
    private final String domain;   // nullable → docker.io
    private final String path;     // library/nginx
    private final String tag;      // nullable → latest// nullable, 있으면 tag보다 우선

    private ImageRef(String domain, String path, String tag) {
        this.domain = domain;
        this.path = path;
        this.tag = tag;
    }
}
