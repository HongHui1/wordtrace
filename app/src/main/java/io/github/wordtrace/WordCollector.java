package io.github.wordtrace;

import java.util.*;

/** A session is a unique vocabulary list, confirmed in consecutive OCR frames. */
public final class WordCollector {
    private final Set<String> ignored, saved = new TreeSet<>();
    private Set<String> previous = Collections.emptySet();
    private long previousTime = -1;
    public WordCollector(Set<String> ignored) { this.ignored = ignored; }
    public void observe(Set<String> words, long now) {
        if (previousTime >= 0 && now - previousTime >= 400 && now - previousTime <= 5000) {
            for (String word : words) if (previous.contains(word) && !ignored.contains(word)) saved.add(word);
        }
        previous = new HashSet<>(words); previousTime = now;
    }
    public void resetPending() { previous = Collections.emptySet(); previousTime = -1; }
    public Map<String, Integer> snapshot() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String word : saved) result.put(word, 1);
        return result;
    }
}
