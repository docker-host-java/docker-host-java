package com.github.dockerhostjava.connection;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class DockerCli {

    /** 인스턴스를 만들지 않는다. */
    private DockerCli() {
    }

    /** CLI 한 줄을 Engine API 호출로 바꾼다. */
    static EngineCall translate(String command) {
        List<String> tokens = tokens(command);
        if (!tokens.isEmpty() && tokens.get(0).equals("docker")) {
            tokens.remove(0);
        }
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("command is empty");
        }
        String name = tokens.get(0);
        List<String> args = tokens.subList(1, tokens.size());
        return switch (name) {
            case "ps" -> containers(args);
            case "images" -> images(args);
            case "version" -> get("/version", args);
            case "info" -> get("/info", args);
            case "inspect" -> container("/json", args);
            case "logs" -> logs(args);
            case "start", "stop", "restart" -> container("/" + name, args);
            case "rm" -> removeContainer(args);
            case "rmi" -> removeImage(args);
            case "pull" -> pull(args);
            case "container" -> containerCommand(args);
            case "image" -> imageCommand(args);
            default -> throw unsupported(name);
        };
    }

    /** container 하위 명령을 Engine API로 바꾼다. */
    private static EngineCall containerCommand(List<String> args) {
        if (args.isEmpty()) {
            throw unsupported("container");
        }
        String name = args.get(0);
        List<String> rest = args.subList(1, args.size());
        return switch (name) {
            case "ls", "ps", "list" -> containers(rest);
            case "inspect" -> container("/json", rest);
            case "logs" -> logs(rest);
            case "start", "stop", "restart" -> container("/" + name, rest);
            case "rm" -> removeContainer(rest);
            default -> throw unsupported("container " + name);
        };
    }

    /** image 하위 명령을 Engine API로 바꾼다. */
    private static EngineCall imageCommand(List<String> args) {
        if (args.isEmpty()) {
            throw unsupported("image");
        }
        String name = args.get(0);
        List<String> rest = args.subList(1, args.size());
        return switch (name) {
            case "ls", "list" -> images(rest);
            case "inspect" -> imageJson(rest);
            case "rm", "rmi" -> removeImage(rest);
            case "pull" -> pull(rest);
            default -> throw unsupported("image " + name);
        };
    }

    /** docker ps를 GET /containers/json으로 바꾼다. */
    private static EngineCall containers(List<String> args) {
        boolean all = false;
        boolean size = false;
        String limit = null;
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            switch (arg) {
                case "-a", "--all" -> all = true;
                case "-s", "--size" -> size = true;
                case "-q", "--quiet" -> {
                }
                case "-n", "--last" -> limit = value(args, ++i, arg);
                default -> {
                    if (arg.startsWith("--last=")) {
                        limit = arg.substring("--last=".length());
                    } else if (arg.startsWith("-")) {
                        throw unsupportedFlag(arg);
                    } else {
                        throw unsupported("ps " + arg);
                    }
                }
            }
        }
        StringBuilder query = new StringBuilder();
        add(query, "all", all ? "true" : null);
        add(query, "size", size ? "true" : null);
        add(query, "limit", limit);
        return EngineCall.get("/containers/json" + query);
    }

    /** docker images를 GET /images/json으로 바꾼다. */
    private static EngineCall images(List<String> args) {
        boolean all = false;
        for (String arg : args) {
            switch (arg) {
                case "-a", "--all" -> all = true;
                case "-q", "--quiet" -> {
                }
                default -> throw unsupportedFlag(arg);
            }
        }
        StringBuilder query = new StringBuilder();
        add(query, "all", all ? "true" : null);
        return EngineCall.get("/images/json" + query);
    }

    /** docker logs를 컨테이너 로그 조회로 바꾼다. */
    private static EngineCall logs(List<String> args) {
        String id = oneTarget(args, "logs");
        return EngineCall.get("/containers/" + encode(id) + "/logs?stdout=true&stderr=true");
    }

    /** 컨테이너 id에 대한 Engine API 호출을 만든다. */
    private static EngineCall container(String suffix, List<String> args) {
        String id = oneTarget(args, suffix);
        String method = suffix.equals("/json") ? "GET" : "POST";
        return new EngineCall(method, "/containers/" + encode(id) + suffix, null);
    }

    /** docker rm을 DELETE /containers/{id}로 바꾼다. */
    private static EngineCall removeContainer(List<String> args) {
        boolean force = false;
        String id = null;
        for (String arg : args) {
            switch (arg) {
                case "-f", "--force" -> force = true;
                default -> {
                    if (arg.startsWith("-")) {
                        throw unsupportedFlag(arg);
                    }
                    if (id != null) {
                        throw unsupported("rm " + id + " " + arg);
                    }
                    id = arg;
                }
            }
        }
        if (id == null) {
            throw new IllegalArgumentException("command target is missing: rm");
        }
        StringBuilder query = new StringBuilder();
        add(query, "force", force ? "true" : null);
        return EngineCall.delete("/containers/" + encode(id) + query);
    }

    /** docker image inspect를 GET /images/{name}/json으로 바꾼다. */
    private static EngineCall imageJson(List<String> args) {
        String name = oneTarget(args, "image inspect");
        return EngineCall.get("/images/" + encode(name) + "/json");
    }

    /** docker rmi를 DELETE /images/{name}으로 바꾼다. */
    private static EngineCall removeImage(List<String> args) {
        String name = oneTarget(args, "rmi");
        return EngineCall.delete("/images/" + encode(name));
    }

    /** docker pull을 POST /images/create로 바꾼다. */
    private static EngineCall pull(List<String> args) {
        String image = oneTarget(args, "pull");
        String fromImage = image;
        String tag = null;
        int slash = image.lastIndexOf('/');
        int colon = image.lastIndexOf(':');
        if (colon > slash) {
            fromImage = image.substring(0, colon);
            tag = image.substring(colon + 1);
        }
        StringBuilder query = new StringBuilder();
        add(query, "fromImage", fromImage);
        add(query, "tag", tag);
        return EngineCall.post("/images/create" + query);
    }

    /** 인자가 없는 GET 호출을 만든다. */
    private static EngineCall get(String path, List<String> args) {
        if (!args.isEmpty()) {
            throw unsupported(path + " " + String.join(" ", args));
        }
        return EngineCall.get(path);
    }

    /** 명령의 대상이 하나인지 확인하고 그 값을 반환한다. */
    private static String oneTarget(List<String> args, String command) {
        List<String> targets = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("-")) {
                throw unsupportedFlag(arg);
            }
            targets.add(arg);
        }
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("command target is missing: " + command);
        }
        if (targets.size() > 1) {
            throw unsupported(command + " " + String.join(" ", targets));
        }
        return targets.get(0);
    }

    /** 플래그 다음 값을 꺼낸다. */
    private static String value(List<String> args, int index, String flag) {
        if (index >= args.size()) {
            throw new IllegalArgumentException("command flag value is missing: " + flag);
        }
        return args.get(index);
    }

    /** 값이 있을 때만 쿼리 문자열에 붙인다. */
    private static void add(StringBuilder query, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        query.append(query.length() == 0 ? '?' : '&');
        query.append(encode(name)).append('=').append(encode(value));
    }

    /** 경로와 쿼리에 넣을 수 있게 인코딩한다. */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** 명령 한 줄을 공백으로 나눈다. */
    private static List<String> tokens(String command) {
        if (command == null || command.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : command.trim().split("\\s+")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /** 지원하지 않는 명령 예외를 만든다. */
    private static IllegalArgumentException unsupported(String command) {
        return new IllegalArgumentException("command is unsupported: " + command);
    }

    /** 지원하지 않는 플래그 예외를 만든다. */
    private static IllegalArgumentException unsupportedFlag(String flag) {
        return new IllegalArgumentException("command flag is unsupported: " + flag);
    }

    record EngineCall(String method, String path, String body) {
        /** 본문 없는 GET 호출을 만든다. */
        static EngineCall get(String path) {
            return new EngineCall("GET", path, null);
        }

        /** 본문 없는 POST 호출을 만든다. */
        static EngineCall post(String path) {
            return new EngineCall("POST", path, null);
        }

        /** 본문 없는 DELETE 호출을 만든다. */
        static EngineCall delete(String path) {
            return new EngineCall("DELETE", path, null);
        }
    }
}
