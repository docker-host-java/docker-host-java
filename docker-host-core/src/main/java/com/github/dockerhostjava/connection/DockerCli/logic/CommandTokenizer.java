package com.github.dockerhostjava.connection.DockerCli.logic;

import java.util.ArrayList;
import java.util.List;

public final class CommandTokenizer {

    private CommandTokenizer() {
    }

    /** 명령 한 줄을 공백으로 나눈다. */
    public static List<String> tokens(String command) {
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
}
