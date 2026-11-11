package com.netwatch.netwatch_api.traffic.connections;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Interpreta {@code tasklist /fo csv /nh}: {@code "chrome.exe","1234","Console","1","150.000 K"}. */
public final class TasklistParser {

    private static final Pattern LINE = Pattern.compile("^\"([^\"]*)\",\"(\\d+)\"");

    private TasklistParser() {
    }

    public static Map<Integer, String> parse(String output) {
        Map<Integer, String> processes = new HashMap<>();
        if (output == null) {
            return processes;
        }
        for (String line : output.split("\\R")) {
            Matcher m = LINE.matcher(line.trim());
            if (m.find() && !m.group(1).isBlank()) {
                processes.putIfAbsent(Integer.parseInt(m.group(2)), m.group(1));
            }
        }
        return processes;
    }
}
