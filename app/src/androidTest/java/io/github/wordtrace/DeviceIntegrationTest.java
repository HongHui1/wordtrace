package io.github.wordtrace;

import android.content.Context;
import android.graphics.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.util.Set;
import org.junit.Assume;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DeviceIntegrationTest {
    @Test public void targetModeRejectsMenusAndVocabularyLists() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        OcrWords words = new OcrWords(context);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            for(String[] labels : new String[][]{{"Learn", "Review"},{"space","spacious","spatial"}}) {
                Bitmap bitmap = Bitmap.createBitmap(1000,700,Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.WHITE);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(Color.BLACK); paint.setTextSize(50);
                for(int i=0;i<labels.length;i++) canvas.drawText(labels[i],100,150+i*150,paint);
                try {
                    Text text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap,0)),30,TimeUnit.SECONDS);
                    assertTrue(text.getText(), words.extract(text,true).isEmpty());
                    assertFalse(text.getText(), words.extract(text,false).isEmpty());
                } finally { bitmap.recycle(); }
            }
        } finally { recognizer.close(); }
    }
    /** Optional local evidence: actual app screens stay outside the source tree. */
    @Test public void realWordCardsFromProvidedScreens() throws Exception {
        String names = InstrumentationRegistry.getArguments().getString("realScreens");
        Assume.assumeNotNull(names);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        OcrWords words = new OcrWords(context);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            for(String name : names.split(",")) {
                assertTrue(name.matches("[a-z-]+"));
                File file = new File(context.getFilesDir(),"ocr-fixtures/"+name+".png");
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                assertNotNull(file.getAbsolutePath(),bitmap);
                float scale = Math.min(1f,960f/Math.max(bitmap.getWidth(),bitmap.getHeight()));
                Bitmap resized = Bitmap.createScaledBitmap(bitmap,Math.round(bitmap.getWidth()*scale),Math.round(bitmap.getHeight()*scale),true);
                if(resized != bitmap) { bitmap.recycle(); bitmap = resized; }
                try {
                    Text text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap,0)),30,TimeUnit.SECONDS);
                    Set<String> expected = name.startsWith("word-") ? Set.of(name.substring(5)) : Set.of();
                    assertEquals(name+": "+text.getText(),expected,words.extract(text,true));
                } finally { bitmap.recycle(); }
            }
        } finally { recognizer.close(); }
    }
    @Test public void bundledOcrRecognizesWordsWithoutDownload() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(1000, 500, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(Color.BLACK); paint.setTextSize(80);
        canvas.drawText("apple banana", 80, 160, paint);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            Text result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 30, TimeUnit.SECONDS);
            assertTrue(result.getText(), WordCounter.tokenize(result.getText()).containsAll(java.util.Arrays.asList("apple", "banana")));
        } finally { recognizer.close(); bitmap.recycle(); }
    }
    @Test public void checkpointRecoversAndExportsBothFormats() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SessionStore store = new SessionStore(context); SessionStore.Session session = store.create();
        session.words.put("apple", 2); session.words.put("banana", 1);
        try {
            store.save(session);
            SessionStore.Session loaded = store.list().stream().filter(s -> s.id.equals(session.id)).findFirst().get();
            assertEquals(Map.of("apple", 2, "banana", 1), loaded.words);
            store.finish(loaded, "interrupted");
            assertTrue(new String(Files.readAllBytes(store.export(loaded, "csv").toPath()), StandardCharsets.UTF_8).contains("\"apple\",2"));
            assertEquals("apple\nbanana\n",new String(Files.readAllBytes(store.export(loaded, "txt").toPath()), StandardCharsets.UTF_8));
        } finally { store.delete(session); }
    }
    @Test public void actualOcrReassemblesSpacedLettersAndRejectsIncompleteWord() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        OcrWords words = new OcrWords(context);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            for (String text : new String[]{"apple", "a p p l e", "a p p l"}) {
                Bitmap bitmap = Bitmap.createBitmap(1100, 600, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.WHITE);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(Color.BLACK); paint.setTextSize(100);
                canvas.drawText(text,100,220,paint);
                paint.setTextSize(28); canvas.drawText("This is an example sentence.",100,350,paint);
                try {
                    Text result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap,0)),30,TimeUnit.SECONDS);
                    java.util.Set<String> actual = words.extract(result,true);
                    if (text.equals("a p p l")) assertTrue(result.getText()+" => "+actual,actual.isEmpty());
                    else assertEquals(result.getText(),java.util.Set.of("apple"),actual);
                } finally { bitmap.recycle(); }
            }
        } finally { recognizer.close(); }
    }
}
