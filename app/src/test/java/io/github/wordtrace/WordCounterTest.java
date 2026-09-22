package io.github.wordtrace;

import org.junit.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

public class WordCounterTest {
    @Test public void continuousScreenIsOneAppearance() {
        WordCounter c = new WordCounter(Set.of());
        for (int t = 0; t < 60000; t += 1000) c.observe("Apple apple APPLE", t);
        assertEquals(Map.of("apple", 1), c.snapshot());
    }
    @Test public void returnAfterAbsenceAddsOne() {
        WordCounter c = new WordCounter(Set.of());
        c.observe("apple", 0); c.observe("banana", 1000); c.observe("banana", 2000); c.observe("apple", 3000);
        assertEquals(Integer.valueOf(2), c.snapshot().get("apple"));
        assertEquals(Integer.valueOf(1), c.snapshot().get("banana"));
    }
    @Test public void briefRecognitionMissDoesNotInflate() {
        WordCounter c = new WordCounter(Set.of());
        c.observe("apple", 0); c.observe("", 1000); c.observe("apple", 2000);
        assertEquals(Integer.valueOf(1), c.snapshot().get("apple"));
    }
    @Test public void punctuationAndIgnoredWords() {
        WordCounter c = new WordCounter(Set.of("next"));
        c.observe("Apple, don't mother-in-law next 123 x I a 苹果", 0);
        assertEquals(Set.of("apple", "don't", "mother-in-law", "i", "a"), c.snapshot().keySet());
    }
    @Test public void resumeDoesNotCountStationaryWordAgain() {
        WordCounter c = new WordCounter(Set.of());
        c.observe("apple", 0); c.resume("apple banana", 10000);
        assertEquals(Map.of("apple", 1, "banana", 1), c.snapshot());
    }
    @Test public void sortedByFrequencyThenAlphabet() {
        WordCounter c = new WordCounter(Set.of());
        c.observe("zebra apple banana", 0); c.observe("apple banana", 1000); c.observe("zebra", 5000);
        assertEquals("zebra", c.snapshot().keySet().iterator().next());
    }
    @Test public void slowFramesDoNotCountAsAbsence() {
        WordCounter c = new WordCounter(Set.of());
        c.observe("apple", 0); c.observe("apple", 10000);
        assertEquals(Integer.valueOf(1), c.snapshot().get("apple"));
    }
    @Test public void exportsContainCountsAndExcelBom() {
        assertEquals("\uFEFFword,count\r\n\"apple\",2\r\n", Exports.csv(Map.of("apple", 2)));
        assertTrue(Exports.txt(Map.of("apple", 2)).contains("apple\t2"));
    }
}
