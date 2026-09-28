package io.github.wordtrace;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class WordRecognitionTest {
    private final WordAssembler assembler = new WordAssembler(Set.of("apple", "app", "banana", "apart", "part", "cat", "don't"));
    private List<WordAssembler.Span> spans(String... parts) {
        List<WordAssembler.Span> result = new ArrayList<>(); float x = 0;
        for (String part : parts) { float width = part.length() * 30; result.add(new WordAssembler.Span(part, x, 0, x + width, 60)); x += width + 18; }
        return result;
    }
    @Test public void spacedGlyphsBecomeWholeWord() {
        assertEquals(Set.of("apple"), assembler.extract(spans("a", "p", "p", "l", "e"), true));
        assertEquals(Set.of("apple"), assembler.extract(spans("a p p l e"), true));
        assertEquals(Set.of("apple"), assembler.extract(spans("ap", "ple"), true));
        assertEquals(Set.of("apple"), assembler.extract(spans("app", "l", "e"), true));
    }
    @Test public void incompleteRunIsNotSavedAsKnownPrefix() {
        assertTrue(assembler.extract(spans("a", "p", "p", "l"), true).isEmpty());
        assertTrue(assembler.extract(spans("appl"), true).isEmpty());
        assertTrue(assembler.extract(spans("p"), true).isEmpty());
    }
    @Test public void neighboringWordsAreNotGlued() {
        assertEquals(Set.of("part", "cat"), assembler.extract(spans("a", "part", "cat"), true));
        assertEquals(Set.of("apple", "banana"), assembler.extract(spans("apple", "banana"), true));
    }
    @Test public void fragmentsAcrossLinesAreNotJoined() {
        assertTrue(assembler.extract(Arrays.asList(new WordAssembler.Span("ap",0,0,60,60), new WordAssembler.Span("ple",0,100,90,160)), true).isEmpty());
    }
    @Test public void smallerExampleTextIsExcluded() {
        List<WordAssembler.Span> spans = spans("apple"); spans.add(new WordAssembler.Span("banana",0,100,100,120));
        assertEquals(Set.of("apple"), assembler.extract(spans,true));
        assertEquals(Set.of("apple","banana"), assembler.extract(spans,false));
    }
    @Test public void stableFramesDeduplicateForEntireSession() {
        WordCollector c = new WordCollector(Set.of());
        c.observe(Set.of("apple"),0); assertTrue(c.snapshot().isEmpty());
        c.observe(Set.of("apple"),1000); c.observe(Set.of("banana"),2000);
        c.observe(Set.of("banana"),3000); c.observe(Set.of("apple"),6000); c.observe(Set.of("apple"),7000);
        assertEquals(Map.of("apple",1,"banana",1),c.snapshot());
    }
    @Test public void animationNoiseAndPauseDoNotConfirmWords() {
        WordCollector c = new WordCollector(Set.of("banana"));
        c.observe(Set.of("app"),0); c.observe(Set.of("apple"),1000); c.resetPending();
        c.observe(Set.of("apple"),2000); assertTrue(c.snapshot().isEmpty());
        c.observe(Set.of("apple","banana"),3000); c.observe(Set.of("banana"),4000);
        assertEquals(Map.of("apple",1),c.snapshot());
    }
    @Test public void plainTextHasOnlyOneWordPerLine() {
        assertEquals("apple\nbanana\n",Exports.txt(Map.of("banana",7,"apple",2)));
        assertEquals("",Exports.txt(Map.of()));
    }
}
