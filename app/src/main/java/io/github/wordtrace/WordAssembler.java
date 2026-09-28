package io.github.wordtrace;

import java.util.*;
import java.util.regex.*;

/** Reassembles OCR fragments using geometry; never guesses missing letters. */
public final class WordAssembler {
    public static final class Span {
        public final String text;
        public final float left, top, right, bottom;
        public Span(String text, float left, float top, float right, float bottom) {
            this.text = text; this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
        float height() { return bottom - top; }
    }
    private static final Pattern WORD = Pattern.compile("[a-z]+(?:['-][a-z]+)*");
    private final Set<String> dictionary;
    public WordAssembler(Set<String> dictionary) { this.dictionary = dictionary; }
    private boolean known(String word) {
        if (dictionary.contains(word)) return true;
        if (word.contains("-")) {
            for (String part : word.split("-")) if (!dictionary.contains(part)) return false;
            return true;
        }
        return false;
    }
    private String clean(String raw) {
        return raw.toLowerCase(Locale.ROOT).replace('’', '\'').replaceAll("^[^a-z]+|[^a-z]+$", "");
    }
    public Set<String> extract(List<Span> input, boolean largestOnly) {
        List<Span> spans = new ArrayList<>();
        for (Span span : input) {
            // Some OCR elements contain spaced glyphs themselves. Preserve their approximate positions.
            Matcher parts = Pattern.compile("\\S+").matcher(span.text);
            while (parts.find()) {
                String word = clean(parts.group());
                if (!WORD.matcher(word).matches() || span.height() <= 0) continue;
                float width = span.right - span.left;
                spans.add(new Span(word, span.left + width * parts.start() / span.text.length(), span.top,
                    span.left + width * parts.end() / span.text.length(), span.bottom));
            }
        }
        float tallest = 0;
        for (Span span : spans) tallest = Math.max(tallest, span.height());
        final float threshold = tallest * .70f;
        if (largestOnly) spans.removeIf(span -> span.height() < threshold);
        spans.sort(Comparator.comparingDouble((Span s) -> s.top).thenComparingDouble(s -> s.left));
        List<List<Span>> rows = new ArrayList<>();
        for (Span span : spans) {
            List<Span> row = null;
            for (List<Span> candidate : rows) {
                Span ref = candidate.get(0);
                float overlap = Math.min(ref.bottom, span.bottom) - Math.max(ref.top, span.top);
                if (overlap >= Math.min(ref.height(), span.height()) * .65f
                    && Math.max(ref.height(), span.height()) <= Math.min(ref.height(), span.height()) * 1.6f) { row = candidate; break; }
            }
            if (row == null) { row = new ArrayList<>(); rows.add(row); }
            row.add(span);
        }
        Set<String> words = new TreeSet<>();
        for (List<Span> row : rows) {
            row.sort(Comparator.comparingDouble(s -> s.left));
            for (int i = 0; i < row.size();) {
                Span first = row.get(i);
                if (first.text.length() == 1) {
                    int end = i + 1;
                    StringBuilder letters = new StringBuilder(first.text);
                    while (end < row.size() && row.get(end).text.length() == 1 && adjacent(row.get(end - 1), row.get(end))) {
                        letters.append(row.get(end).text); end++;
                    }
                    if (end - i >= 2) {
                        // Evaluate the whole glyph run, never accept "app" from incomplete "a p p l".
                        if (letters.length() >= 3 && known(letters.toString())) words.add(letters.toString());
                        i = end; continue;
                    }
                }
                StringBuilder joined = new StringBuilder();
                int bestEnd = -1, singles = 0;
                boolean invalidFragment = false;
                String best = null;
                for (int j = i; j < row.size() && j < i + 24; j++) {
                    Span current = row.get(j);
                    if (j > i) {
                        Span previous = row.get(j - 1);
                        if (!adjacent(previous, current)) break;
                    }
                    joined.append(current.text);
                    if (joined.length() > 40) break;
                    if (current.text.length() == 1) singles++;
                    else if (!known(current.text)) invalidFragment = true;
                    // Do not turn real adjacent words such as "a part" into "apart".
                    if (j > i && (singles >= 3 || (singles >= 2 && first.text.length() > 1) || invalidFragment) && known(joined.toString())) {
                        best = joined.toString(); bestEnd = j;
                    }
                }
                if (best != null) { words.add(best); i = bestEnd + 1; }
                else {
                    // Isolated letters and unknown fragments are deliberately not words.
                    if (first.text.length() >= 2 && known(first.text)) words.add(first.text);
                    i++;
                }
            }
        }
        return words;
    }
    private boolean adjacent(Span previous, Span current) {
        float gap = current.left - previous.right;
        return gap >= -2 && gap <= Math.min(current.height(), previous.height()) * .65f;
    }
}
