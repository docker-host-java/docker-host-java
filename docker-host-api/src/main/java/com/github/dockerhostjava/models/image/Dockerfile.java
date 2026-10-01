package com.github.dockerhostjava.models.image;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

// 불변 객체
// 실행할 때마다 새 인스턴스를 반환
public final class Dockerfile {

    // 명령 뭉탱이
    private final List<String> instructions;

    private Dockerfile(List<String> instructions) {
        this.instructions = instructions;
    }

    // FROM은 ARG 대입이나 스테이지 이름도 받을 수 있어서 ImageRef가 아닌 String
    public static Dockerfile from(String image) {
        return new Dockerfile(List.of("FROM " + image));
    }

    public Dockerfile arg(String name) {
        return append("ARG " + name);
    }

    public Dockerfile arg(String name, String defaultValue) {
        return append("ARG " + name + "=" + quote(defaultValue));
    }

    public Dockerfile env(String key, String value) {
        return append("ENV " + key + "=" + quote(value));
    }

    public Dockerfile label(String key, String value) {
        return append("LABEL " + key + "=" + quote(value));
    }

    public Dockerfile workdir(String path) {
        return append("WORKDIR " + path);
    }

    public Dockerfile user(String user) {
        return append("USER " + user);
    }

    public Dockerfile run(String command) {     // shell form, /bin/sh -c 로 실행
        return append("RUN " + command);
    }

    public Dockerfile copy(String src, String dest) {
        return append("COPY " + jsonArray(src, dest));
    }

    public Dockerfile add(String src, String dest) {
        return append("ADD " + jsonArray(src, dest));
    }

    public Dockerfile expose(int port) {
        return append("EXPOSE " + port);
    }

    public Dockerfile volume(String path) {
        return append("VOLUME " + jsonArray(path));
    }

    public Dockerfile entrypoint(String... command) {   // exec form
        return append("ENTRYPOINT " + jsonArray(command));
    }

    public Dockerfile cmd(String... command) {          // exec form
        return append("CMD " + jsonArray(command));
    }

    public String content() {
        return String.join("\n", instructions) + "\n";
    }

    @Override
    public String toString() {
        return content();
    }

    private Dockerfile append(String instruction) {
        List<String> next = new ArrayList<>(instructions);
        next.add(instruction);
        return new Dockerfile(List.copyOf(next));
    }

    private static String jsonArray(String... values) {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (String value : values) {
            joiner.add(quote(value));
        }
        return joiner.toString();
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
