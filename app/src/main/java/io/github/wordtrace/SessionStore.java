package io.github.wordtrace;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Atomic checkpoints keep words even if Android kills the process. No screenshots are stored. */
public final class SessionStore {
    public static final class Session {
        public String id;
        public long started, ended;
        public String status;
        public Map<String, Integer> words = new LinkedHashMap<>();
        public int total() { int n = 0; for (int count : words.values()) n += count; return n; }
    }
    private final File dir;
    public SessionStore(Context context) {
        dir = new File(context.getFilesDir(), "sessions");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("无法创建记录目录");
    }
    public Session create() {
        Session s = new Session(); s.started = System.currentTimeMillis(); s.status = "recording";
        s.id = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date(s.started)) + "-" + UUID.randomUUID().toString().substring(0, 6);
        return s;
    }
    public synchronized void save(Session s) throws IOException {
        try {
            JSONObject data = new JSONObject();
            data.put("id", s.id); data.put("started", s.started); data.put("ended", s.ended);
            data.put("status", s.status); data.put("words", new JSONObject(s.words));
            write(new File(dir, s.id + ".json"), data.toString(2));
        } catch (org.json.JSONException e) { throw new IOException(e); }
    }
    public synchronized void finish(Session s, String status) throws IOException {
        s.ended = System.currentTimeMillis(); s.status = status;
        save(s); // Preserve canonical data even if an export fails.
        export(s, "txt");
    }
    public synchronized File export(Session s, String format) throws IOException {
        if (!format.equals("csv") && !format.equals("txt")) throw new IOException("不支持的格式");
        File file = new File(dir, "wordtrace-" + s.id + "." + format);
        write(file, format.equals("csv") ? Exports.csv(s.words) : Exports.txt(s.words));
        return file;
    }
    public synchronized List<Session> list() {
        List<Session> result = new ArrayList<>();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return result;
        for (File file : files) {
            try {
                JSONObject o = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
                Session s = new Session(); s.id = o.getString("id"); s.started = o.getLong("started");
                s.ended = o.optLong("ended"); s.status = o.optString("status", "interrupted");
                JSONObject words = o.getJSONObject("words"); Map<String, Integer> raw = new LinkedHashMap<>();
                Iterator<String> keys = words.keys();
                while (keys.hasNext()) { String key = keys.next(); raw.put(key, words.getInt(key)); }
                WordCounter counter = new WordCounter(java.util.Collections.emptySet()); counter.restore(raw); s.words = counter.snapshot();
                result.add(s);
            } catch (Exception ignored) { /* A corrupt file never hides intact sessions. */ }
        }
        result.sort(Comparator.comparingLong((Session s) -> s.started).reversed());
        return result;
    }
    public synchronized void recover() throws IOException {
        for (Session s : list()) if (s.status.equals("recording")) finish(s, "interrupted");
    }
    public synchronized void delete(Session s) throws IOException {
        for (String name : new String[]{s.id + ".json", "wordtrace-" + s.id + ".csv", "wordtrace-" + s.id + ".txt"}) {
            File f = new File(dir, name);
            if (f.exists() && !f.delete()) throw new IOException("无法删除文件");
        }
    }
    private static void write(File file, String text) throws IOException {
        AtomicFile atomic = new AtomicFile(file); FileOutputStream out = null;
        try { out = atomic.startWrite(); out.write(text.getBytes(StandardCharsets.UTF_8)); atomic.finishWrite(out); }
        catch (IOException e) { atomic.failWrite(out); throw e; }
    }
}
