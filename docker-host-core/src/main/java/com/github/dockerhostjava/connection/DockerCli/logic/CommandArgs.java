package com.github.dockerhostjava.connection.DockerCli.logic;

import java.util.ArrayList;
import java.util.List;

public final class CommandArgs {

    private CommandArgs() {
    }

    /** 명령의 대상이 하나인지 확인하고 그 값을 반환한다. */
    public static String oneTarget(List<String> args, String command) {
        List<String> targets = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("-")) {
                throw CommandErrors.unsupportedFlag(arg);
            }
            targets.add(arg);
        }
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("command target is missing: " + command);
        }
        if (targets.size() > 1) {
            throw CommandErrors.unsupported(command + " " + String.join(" ", targets));
        }
        return targets.get(0);
    }

    /** 플래그 다음 값을 꺼낸다. */
    public static String value(List<String> args, int index, String flag) {
        if (index >= args.size()) {
            throw new IllegalArgumentException("command flag value is missing: " + flag);
        }
        return args.get(index);
    }
}
