package io.github.wordtrace;

import android.content.Context;
import android.graphics.Rect;
import com.google.mlkit.vision.text.Text;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class OcrWords {
    private final WordAssembler assembler;
    OcrWords(Context context) throws IOException {
        Set<String> dictionary = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open("english-words.txt"), StandardCharsets.UTF_8))) {
            String word;
            while ((word = reader.readLine()) != null) dictionary.add(word);
        }
        assembler = new WordAssembler(dictionary);
    }
    Set<String> extract(Text text, boolean largestOnly) {
        List<WordAssembler.Span> spans = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) for (Text.Line line : block.getLines())
            for (Text.Element element : line.getElements()) {
                Rect box = element.getBoundingBox();
                if (box != null) spans.add(new WordAssembler.Span(element.getText(), box.left, box.top, box.right, box.bottom));
            }
        return assembler.extract(spans, largestOnly);
    }
}
