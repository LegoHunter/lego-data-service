package com.vattima.lego.inventory.seed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses {@code <command> --name=value --flag} arguments, rejecting options a command does not take.
 */
final class CliArguments {

    private final String command;
    private final Map<String, String> options;

    private CliArguments(String command, Map<String, String> options) {
        this.command = command;
        this.options = options;
    }

    static CliArguments parse(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("A command is required: export, import, status or teardown");
        }
        Map<String, String> options = new HashMap<>();
        for (String arg : Arrays.copyOfRange(args, 1, args.length)) {
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument '" + arg + "'; options look like --name=value");
            }
            int equals = arg.indexOf('=');
            String name = equals < 0 ? arg.substring(2) : arg.substring(2, equals);
            String value = equals < 0 ? "true" : arg.substring(equals + 1);
            if (options.put(name, value) != null) {
                throw new IllegalArgumentException("--" + name + " is given twice");
            }
        }
        return new CliArguments(args[0], options);
    }

    String command() {
        return command;
    }

    void allowOnly(Set<String> allowed) {
        for (String name : options.keySet()) {
            if (!allowed.contains(name)) {
                throw new IllegalArgumentException("'" + command + "' does not take --" + name);
            }
        }
    }

    String required(String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("--" + name + " is required for '" + command + "'");
        }
        return value;
    }

    String optional(String name) {
        return options.get(name);
    }

    boolean flag(String name) {
        return Boolean.parseBoolean(options.getOrDefault(name, "false"));
    }

    int integer(String name, int defaultValue) {
        String value = options.get(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--" + name + " must be a whole number, not '" + value + "'");
        }
    }

    List<String> list(String name) {
        String value = options.get(name);
        List<String> result = new ArrayList<>();
        if (value != null) {
            for (String part : value.split(",")) {
                if (!part.isBlank()) {
                    result.add(part.trim());
                }
            }
        }
        return result;
    }
}
