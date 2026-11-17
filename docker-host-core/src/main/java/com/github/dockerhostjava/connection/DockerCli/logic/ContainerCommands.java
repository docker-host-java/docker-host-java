package com.github.dockerhostjava.connection.DockerCli.logic;

import java.util.List;

import static com.github.dockerhostjava.connection.DockerCli.logic.QueryEncoding.add;
import static com.github.dockerhostjava.connection.DockerCli.logic.QueryEncoding.encode;

public final class ContainerCommands {

    private ContainerCommands() {
    }

    /** container 하위 명령을 Engine API로 바꾼다. */
    public static EngineCall container(List<String> args) {
        if (args.isEmpty()) {
            throw CommandErrors.unsupported("container");
        }
        String name = args.get(0);
        List<String> rest = args.subList(1, args.size());
        return switch (name) {
            case "ls", "ps", "list" -> list(rest);
            case "inspect" -> byId("/json", rest);
            case "logs" -> logs(rest);
            case "start", "stop", "restart" -> byId("/" + name, rest);
            case "rm" -> remove(rest);
            default -> throw CommandErrors.unsupported("container " + name);
        };
    }

    /** docker ps를 GET /containers/json으로 바꾼다. */
    public static EngineCall list(List<String> args) {
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
                case "-n", "--last" -> limit = CommandArgs.value(args, ++i, arg);
                default -> {
                    if (arg.startsWith("--last=")) {
                        limit = arg.substring("--last=".length());
                    } else if (arg.startsWith("-")) {
                        throw CommandErrors.unsupportedFlag(arg);
                    } else {
                        throw CommandErrors.unsupported("ps " + arg);
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

    /** docker logs를 컨테이너 로그 조회로 바꾼다. */
    public static EngineCall logs(List<String> args) {
        String id = CommandArgs.oneTarget(args, "logs");
        return EngineCall.get("/containers/" + encode(id) + "/logs?stdout=true&stderr=true");
    }

    /** 컨테이너 id에 대한 Engine API 호출을 만든다. */
    public static EngineCall byId(String suffix, List<String> args) {
        String id = CommandArgs.oneTarget(args, suffix);
        String method = suffix.equals("/json") ? "GET" : "POST";
        return new EngineCall(method, "/containers/" + encode(id) + suffix, null);
    }

    /** docker rm을 DELETE /containers/{id}로 바꾼다. */
    public static EngineCall remove(List<String> args) {
        boolean force = false;
        String id = null;
        for (String arg : args) {
            switch (arg) {
                case "-f", "--force" -> force = true;
                default -> {
                    if (arg.startsWith("-")) {
                        throw CommandErrors.unsupportedFlag(arg);
                    }
                    if (id != null) {
                        throw CommandErrors.unsupported("rm " + id + " " + arg);
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
}
