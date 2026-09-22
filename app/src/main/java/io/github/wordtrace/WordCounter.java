package io.github.wordtrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Counts appearances, not OCR frames. A brief OCR miss must not inflate counts. */
public final class WordCounter {
    private static final Pattern TOKEN = Pattern.compile("(?<![A-Za-z0-9])[A-Za-z]+(?:['’][A-Za-z]+)*(?:-[A-Za-z]+)*(?![A-Za-z0-9])");
    private static final long ABSENCE_MILLIS = 2000;
    private final Map<String, Integer> counts = new HashMap<>();
    private final Map<String, Long> absentSince = new HashMap<>();
    private final Set<String> visible = new HashSet<>();
    private final Set<String> ignored;

    public WordCounter(Set<String> ignored) { this.ignored = new HashSet<>(ignored); }

    public static Set<String> tokenize(String text) {
        Set<String> words = new HashSet<>();
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            String word = matcher.group().replace('’', '\'').toLowerCase(Locale.ROOT);
            if (word.length() <= 40 && (word.length() > 1 || word.equals("a") || word.equals("i"))) words.add(word);
        }
        return words;
    }

    public synchronized boolean observe(String text, long now) {
        boolean changed = false;
        Set<String> incoming = tokenize(text);
        for (String word : visible) if (!incoming.contains(word)) absentSince.putIfAbsent(word, now);
        for (String word : incoming) {
            if (ignored.contains(word)) continue;
            Long absent = absentSince.remove(word);
            if (!counts.containsKey(word) || (absent != null && now - absent >= ABSENCE_MILLIS)) {
                counts.put(word, counts.getOrDefault(word, 0) + 1);
                changed = true;
            }
        }
        visible.addAll(incoming);
        return changed;
    }

    /** A pause is not a new exposure. Resume uses a warm frame without incrementing old words. */
    public synchronized void resume(String text, long now) {
        for (String word : tokenize(text)) absentSince.remove(word);
        observe(text, now);
    }

    public synchronized void restore(Map<String, Integer> saved) { counts.putAll(saved); }
    public synchronized Map<String, Integer> snapshot() {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> {
            int order = Integer.compare(b.getValue(), a.getValue());
            return order != 0 ? order : a.getKey().compareTo(b.getKey());
        });
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : sorted) result.put(entry.getKey(), entry.getValue());
        return Collections.unmodifiableMap(result);
    }
}
