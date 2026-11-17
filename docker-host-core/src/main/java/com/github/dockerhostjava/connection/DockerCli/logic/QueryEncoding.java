package com.github.dockerhostjava.connection.DockerCli.logic;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class QueryEncoding {

    private QueryEncoding() {
    }

    /** 값이 있을 때만 쿼리 문자열에 붙인다. */
    public static void add(StringBuilder query, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        query.append(query.length() == 0 ? '?' : '&');
        query.append(encode(name)).append('=').append(encode(value));
    }

    /** 경로와 쿼리에 넣을 수 있게 인코딩한다. */
    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
