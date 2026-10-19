package com.github.dockerhostjava.connection;

import java.nio.file.Path;

public class TlsCerts {

    private Path caPem;
    private Path certPem;
    private Path keyPem;

    public TlsCerts caPem(Path caPem) {
        this.caPem = caPem;
        return this;
    }

    public TlsCerts certPem(Path certPem) {
        this.certPem = certPem;
        return this;
    }

    public TlsCerts keyPem(Path keyPem) {
        this.keyPem = keyPem;
        return this;
    }

    Path caPem() {
        return caPem;
    }

    Path certPem() {
        return certPem;
    }

    Path keyPem() {
        return keyPem;
    }
}
