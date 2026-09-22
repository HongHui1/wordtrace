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
        StringBuilder out = new StringBuilder("WordTrace 单词记录\n单词\t出现次数\n");
        words.forEach((word, count) -> out.append(word).append('\t').append(count).append('\n'));
        return out.toString();
    }
}
