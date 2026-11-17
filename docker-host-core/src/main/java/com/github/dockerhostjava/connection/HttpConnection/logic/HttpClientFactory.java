package com.github.dockerhostjava.connection.HttpConnection.logic;

import com.github.dockerhostjava.connection.TlsCerts;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

public final class HttpClientFactory {

    private HttpClientFactory() {
    }

    /** TLS 여부와 제한 시간에 맞는 HttpClient를 만든다. */
    public static HttpClient create(boolean tls, TlsCerts tlsCerts, URI address, Duration connectTimeout) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(connectTimeout);
        if (tls) {
            builder.sslContext(TlsContextLoader.load(tlsCerts, address));
        }
        return builder.build();
    }
}
