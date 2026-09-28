package io.github.wordtrace;

import java.util.Map;

public final class Exports {
    private Exports() {}
    public static String csv(Map<String, Integer> words) {
        StringBuilder out = new StringBuilder("\uFEFFword,count\r\n");
        for (Map.Entry<String, Integer> entry : words.entrySet()) {
            out.append('"').append(entry.getKey().replace("\"", "\"\"")).append("\",").append(entry.getValue()).append("\r\n");
        }
        return out.toString();
    }
    public static String txt(Map<String, Integer> words) {
        StringBuilder out = new StringBuilder();
        for (String word : new java.util.TreeSet<>(words.keySet())) out.append(word).append('\n');
        return out.toString();
    }
}
