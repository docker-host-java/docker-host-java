package com.github.dockerhostjava.connection.DockerCli;

import com.github.dockerhostjava.connection.DockerCli.logic.CommandErrors;
import com.github.dockerhostjava.connection.DockerCli.logic.CommandTokenizer;
import com.github.dockerhostjava.connection.DockerCli.logic.ContainerCommands;
import com.github.dockerhostjava.connection.DockerCli.logic.EngineCall;
import com.github.dockerhostjava.connection.DockerCli.logic.ImageCommands;

import java.util.List;

public final class DockerCli {

    /** 인스턴스를 만들지 않는다. */
    private DockerCli() {
    }

    /** CLI 한 줄을 Engine API 호출로 바꾼다. */
    public static EngineCall translate(String command) {
        List<String> tokens = CommandTokenizer.tokens(command);
        if (!tokens.isEmpty() && tokens.get(0).equals("docker")) {
            tokens.remove(0);
        }
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("command is empty");
        }
        String name = tokens.get(0);
        List<String> args = tokens.subList(1, tokens.size());
        return switch (name) {
            case "ps" -> ContainerCommands.list(args);
            case "images" -> ImageCommands.list(args);
            case "version" -> get("/version", args);
            case "info" -> get("/info", args);
            case "inspect" -> ContainerCommands.byId("/json", args);
            case "logs" -> ContainerCommands.logs(args);
            case "start", "stop", "restart" -> ContainerCommands.byId("/" + name, args);
            case "rm" -> ContainerCommands.remove(args);
            case "rmi" -> ImageCommands.remove(args);
            case "pull" -> ImageCommands.pull(args);
            case "container" -> ContainerCommands.container(args);
            case "image" -> ImageCommands.image(args);
            default -> throw CommandErrors.unsupported(name);
        };
    }

    /** 인자가 없는 GET 호출을 만든다. */
    private static EngineCall get(String path, List<String> args) {
        if (!args.isEmpty()) {
            throw CommandErrors.unsupported(path + " " + String.join(" ", args));
        }
        return EngineCall.get(path);
    }
}
