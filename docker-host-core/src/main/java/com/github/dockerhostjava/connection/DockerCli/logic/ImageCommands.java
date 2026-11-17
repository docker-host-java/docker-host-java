package com.github.dockerhostjava.connection.DockerCli.logic;

import java.util.List;

import static com.github.dockerhostjava.connection.DockerCli.logic.QueryEncoding.add;
import static com.github.dockerhostjava.connection.DockerCli.logic.QueryEncoding.encode;

public final class ImageCommands {

    private ImageCommands() {
    }

    /** image 하위 명령을 Engine API로 바꾼다. */
    public static EngineCall image(List<String> args) {
        if (args.isEmpty()) {
            throw CommandErrors.unsupported("image");
        }
        String name = args.get(0);
        List<String> rest = args.subList(1, args.size());
        return switch (name) {
            case "ls", "list" -> list(rest);
            case "inspect" -> inspect(rest);
            case "rm", "rmi" -> remove(rest);
            case "pull" -> pull(rest);
            default -> throw CommandErrors.unsupported("image " + name);
        };
    }

    /** docker images를 GET /images/json으로 바꾼다. */
    public static EngineCall list(List<String> args) {
        boolean all = false;
        for (String arg : args) {
            switch (arg) {
                case "-a", "--all" -> all = true;
                case "-q", "--quiet" -> {
                }
                default -> throw CommandErrors.unsupportedFlag(arg);
            }
        }
        StringBuilder query = new StringBuilder();
        add(query, "all", all ? "true" : null);
        return EngineCall.get("/images/json" + query);
    }

    /** docker image inspect를 GET /images/{name}/json으로 바꾼다. */
    public static EngineCall inspect(List<String> args) {
        String name = CommandArgs.oneTarget(args, "image inspect");
        return EngineCall.get("/images/" + encode(name) + "/json");
    }

    /** docker rmi를 DELETE /images/{name}으로 바꾼다. */
    public static EngineCall remove(List<String> args) {
        String name = CommandArgs.oneTarget(args, "rmi");
        return EngineCall.delete("/images/" + encode(name));
    }

    /** docker pull을 POST /images/create로 바꾼다. */
    public static EngineCall pull(List<String> args) {
        String image = CommandArgs.oneTarget(args, "pull");
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
}
