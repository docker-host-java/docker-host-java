package com.github.dockerhostjava.connection.HttpConnection.logic;

import com.github.dockerhostjava.connection.TlsCerts;

import javax.net.ssl.SSLContext;
import java.net.URI;

public final class TlsContextLoader {

    private TlsContextLoader() {
    }

    /** 저장된 PEM으로 SSLContext를 만든다. */
    public static SSLContext load(TlsCerts tlsCerts, URI address) {
        try {
            return tlsCerts.sslContext();
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "tls certs could not be applied: uri=" + address
                            + " ca=" + tlsCerts.caPem()
                            + " cert=" + tlsCerts.certPem()
                            + " key=" + tlsCerts.keyPem()
                            + " error=" + e.getMessage(),
                    e);
        }
    }
}
