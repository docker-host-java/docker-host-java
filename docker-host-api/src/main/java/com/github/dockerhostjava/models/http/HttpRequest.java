package com.github.dockerhostjava.models.http;

import java.util.Map;

/**
 * 전송 수단(TCP/TLS, Unix 도메인 소켓, SSH 터널, Named Pipe)과 무관하게 공유하는 HTTP/1.1 요청.
 *
 * <ul>
 *   <li>method: 요청 종류 (GET/POST/PUT/DELETE 등)</li>
 *   <li>path: 요청 경로 및 쿼리 스트링 (예: "/containers/json?all=true")</li>
 *   <li>headers: 부가 헤더 (Content-Type, Accept 등)</li>
 *   <li>body: 본문 바이트. 없으면 null.</li>
 * </ul>
 *
 * <p>HTTP/1.1 필수 헤더인 {@code Host} 값은 전송 계층이 자신이 연결된 대상에 맞게 직렬화 시점에
 * 주입한다 (TCP/TLS = "host:port", Unix 소켓/Named Pipe = "localhost" 등). 요청 객체 자체는
 * 어느 호스트로 갈지와 무관하게 재사용 가능하다.
 *
 * <h2>사용 예시</h2>
 *
 * <pre>{@code
 * // 1) docker ps  ->  GET /containers/json
 * HttpRequest listContainers = new HttpRequest(
 *         HttpMethod.GET,
 *         "/containers/json",
 *         Map.of("Accept", "application/json"),
 *         null);
 *
 * // 2) docker ps -a  ->  GET /containers/json?all=true
 * HttpRequest listAll = new HttpRequest(
 *         HttpMethod.GET,
 *         "/containers/json?all=true",
 *         Map.of(),
 *         null);
 *
 * // 3) docker start <id>  ->  POST /containers/{id}/start
 * HttpRequest start = new HttpRequest(
 *         HttpMethod.POST,
 *         "/containers/abc123/start",
 *         Map.of(),
 *         null);
 *
 * // 4) docker rm -f <id>  ->  DELETE /containers/{id}?force=true
 * HttpRequest remove = new HttpRequest(
 *         HttpMethod.DELETE,
 *         "/containers/abc123?force=true",
 *         Map.of(),
 *         null);
 *
 * // 5) docker pull nginx:latest  ->  POST /images/create?fromImage=nginx&tag=latest
 * HttpRequest pull = new HttpRequest(
 *         HttpMethod.POST,
 *         "/images/create?fromImage=nginx&tag=latest",
 *         Map.of(),
 *         null);
 *
 * // 6) 컨테이너 생성 (본문 있는 요청)  ->  POST /containers/create
 * byte[] body = """
 *         {"Image":"nginx:latest","HostConfig":{"PublishAllPorts":true}}
 *         """.getBytes(StandardCharsets.UTF_8);
 * HttpRequest create = new HttpRequest(
 *         HttpMethod.POST,
 *         "/containers/create",
 *         Map.of("Content-Type", "application/json"),
 *         body);
 *
 * // 호스트에 전달 — 전송 수단(TCP/Unix/SSH/Named Pipe)에 상관없이 같은 객체를 그대로 사용한다.
 * Optional<String> response = dockerHost.command(listContainers);
 * }</pre>
 *
 * <p>전송 계층은 이 record를 "GET /containers/json HTTP/1.1\r\nHost: ...\r\n\r\n" 형태의
 * HTTP/1.1 평문 바이트열로 직렬화해 각자의 바이트 스트림(소켓/파이프/채널)으로 흘려 보낸다.
 */
public record HttpRequest(
        HttpMethod method,
        String path,
        Map<String, String> headers,
        byte[] body
) {

    public HttpRequest {
        if (method == null) {
            throw new IllegalArgumentException("http request method is missing");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("http request path is missing");
        }
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
