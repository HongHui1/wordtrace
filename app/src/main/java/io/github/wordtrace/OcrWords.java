package io.github.wordtrace;

import android.content.Context;
import android.graphics.Rect;
import com.google.mlkit.vision.text.Text;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class OcrWords {
    private static Set<String> cachedDictionary;
    private final WordAssembler assembler;
    OcrWords(Context context) throws IOException {
        assembler = new WordAssembler(dictionary(context));
    }
    private static synchronized Set<String> dictionary(Context context) throws IOException {
        if (cachedDictionary != null) return cachedDictionary;
        Set<String> dictionary = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open("english-words.txt"), StandardCharsets.UTF_8))) {
            String word;
            while ((word = reader.readLine()) != null) dictionary.add(word);
        }
        cachedDictionary = Collections.unmodifiableSet(dictionary);
        return cachedDictionary;
    }
    Set<String> extract(Text text, boolean largestOnly) {
        List<WordAssembler.Span> spans = new ArrayList<>();
        Set<String> target = Collections.emptySet();
        float largest = 0, second = 0;
        for (Text.TextBlock block : text.getTextBlocks()) for (Text.Line line : block.getLines()) {
            List<WordAssembler.Span> row = new ArrayList<>();
            float size = 0;
            for (Text.Element element : line.getElements()) {
                Rect box = element.getBoundingBox();
                if (box != null) {
                    WordAssembler.Span span = new WordAssembler.Span(element.getText(), box.left, box.top, box.right, box.bottom);
                    row.add(span); spans.add(span);
                    if (element.getText().matches(".*[a-zA-Z].*")) size = Math.max(size, box.height());
                }
            }
            if (largestOnly) {
                Set<String> words = assembler.extract(row, true);
                if (size > 0) {
                    if (size > largest) { second = largest; largest = size; target = words; }
                    else second = Math.max(second, size);
                }
            }
        }
        // A learning card has one dominant heading. Menus and vocabulary lists
        // have several equal-size lines and must not become target words.
        return largestOnly ? (largest >= second * 1.35f ? target : Collections.emptySet()) : assembler.extract(spans, false);
    }
}
