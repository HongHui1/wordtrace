package io.github.wordtrace;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.hardware.display.*;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import androidx.core.app.NotificationCompat;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CaptureService extends Service {
    public static final String STOP = "io.github.wordtrace.STOP", PAUSE = "io.github.wordtrace.PAUSE";
    public static volatile boolean active, paused, saving;
    public static volatile int unique, total;
    public static volatile String message = "准备开始";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService disk = Executors.newSingleThreadExecutor();
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private TextRecognizer recognizer;
    private SessionStore store;
    private SessionStore.Session session;
    private WordCounter counter;
    private boolean stopping, busy, warmResume, hidden;
    private int width, height, density, top, bottom, errors;
    private boolean largeOnly;
    private final Runnable sample = new Runnable() {
        @Override public void run() {
            if (stopping) return;
            if (reader != null) onImage(reader);
            main.postDelayed(this, 1000);
        }
    };

    private final MediaProjection.Callback callback = new MediaProjection.Callback() {
        @Override public void onStop() { finishRecording("interrupted"); }
        @Override public void onCapturedContentResize(int w, int h) { if (!stopping && display != null) resize(w, h); }
        @Override public void onCapturedContentVisibilityChanged(boolean visible) {
            hidden = !visible; if (visible) warmResume = true;
        }
    };
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { finishRecording("interrupted"); }
    };

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("recording", "单词记录", NotificationManager.IMPORTANCE_LOW));
        manager.createNotificationChannel(new NotificationChannel("saved", "记录已保存", NotificationManager.IMPORTANCE_DEFAULT));
        androidx.core.content.ContextCompat.registerReceiver(this, screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        if (STOP.equals(intent.getAction())) { finishRecording("complete"); return START_NOT_STICKY; }
        if (PAUSE.equals(intent.getAction())) {
            if (active && !stopping) { paused = !paused; warmResume = !paused; message = paused ? "已暂停" : "识别中"; updateNotification(); }
            return START_NOT_STICKY;
        }
        if (active || stopping) return START_NOT_STICKY;
        try {
            Notification notification = notification("正在准备离线识别");
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            else startForeground(1, notification);
            active = true;
            Intent data = intent.getParcelableExtra("data");
            if (data == null) throw new IllegalArgumentException("Missing capture consent");
            store = new SessionStore(this); store.recover();
            session = store.create(); store.save(session);
            SharedPreferences settings = getSharedPreferences("settings", MODE_PRIVATE);
            counter = new WordCounter(WordCounter.tokenize(settings.getString("ignored", "")));
            top = settings.getInt("top", 0); bottom = settings.getInt("bottom", 100);
            largeOnly = settings.getBoolean("large", false);
            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            projection = getSystemService(MediaProjectionManager.class).getMediaProjection(intent.getIntExtra("code", Activity.RESULT_OK), data);
            projection.registerCallback(callback, main);
            DisplayMetrics metrics = new DisplayMetrics();
            getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(metrics);
            density = metrics.densityDpi;
            setSize(metrics.widthPixels, metrics.heightPixels);
            reader = newReader();
            display = projection.createVirtualDisplay("WordTrace", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, main);
            active = true; paused = false; saving = false; unique = 0; total = 0; message = "识别中";
            main.post(sample);
            updateNotification();
        } catch (Exception e) {
            recordError("屏幕共享启动失败，请重新授权。" + e.getClass().getSimpleName());
            finishRecording("interrupted");
        }
        return START_NOT_STICKY;
    }
    private void setSize(int w, int h) {
        float factor = Math.min(1f, 1600f / Math.max(w, h));
        width = Math.max(1, Math.round(w * factor)); height = Math.max(1, Math.round(h * factor));
    }
    private ImageReader newReader() {
        ImageReader r = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        return r;
    }
    private void resize(int w, int h) {
        int oldW = width, oldH = height; setSize(w, h);
        if (oldW == width && oldH == height) return;
        ImageReader old = reader;
        try {
            reader = newReader(); display.resize(width, height, density); display.setSurface(reader.getSurface());
            warmResume = true;
        } catch (RuntimeException e) { recordError("屏幕尺寸变化，记录已保存，请重新开始。"); finishRecording("interrupted"); }
        finally { old.close(); }
    }
    private void onImage(ImageReader source) {
        Image image = null;
        try {
            image = source.acquireLatestImage();
            if (image == null || stopping || !active || busy) return;
            if (paused || hidden || MainActivity.visible || getSystemService(KeyguardManager.class).isKeyguardLocked()) { warmResume = true; return; }
            Image.Plane plane = image.getPlanes()[0]; ByteBuffer buffer = plane.getBuffer();
            int rowPixels = plane.getRowStride() / plane.getPixelStride();
            Bitmap padded = Bitmap.createBitmap(rowPixels, image.getHeight(), Bitmap.Config.ARGB_8888);
            padded.copyPixelsFromBuffer(buffer);
            int y = image.getHeight() * top / 100;
            int cropHeight = Math.max(1, image.getHeight() * (bottom - top) / 100);
            Bitmap crop = Bitmap.createBitmap(padded, 0, y, image.getWidth(), Math.min(cropHeight, image.getHeight() - y));
            if (crop != padded) padded.recycle();
            busy = true;
            recognizer.process(InputImage.fromBitmap(crop, 0))
                .addOnSuccessListener(result -> {
                    if (!stopping && active && !paused && !hidden && !MainActivity.visible) {
                        errors = 0;
                        String text = selectText(result);
                        if (warmResume) { counter.resume(text, SystemClock.elapsedRealtime()); warmResume = false; }
                        else counter.observe(text, SystemClock.elapsedRealtime());
                        Map<String, Integer> words = counter.snapshot();
                        unique = words.size(); total = 0; for (int n : words.values()) total += n;
                        message = unique == 0 ? "等待英文内容" : "已记 " + unique + " 词";
                        if (!session.words.equals(words)) { session.words = words; checkpoint(); updateNotification(); }
                    }
                })
                .addOnFailureListener(e -> {
                    if (!stopping) { message = "识别失败，正在重试"; if (++errors >= 5) { recordError("离线识别连续失败，已保存记录。请重启应用后重试。"); finishRecording("interrupted"); } }
                })
                .addOnCompleteListener(task -> { crop.recycle(); busy = false; });
        } catch (RuntimeException e) {
            if (!stopping) { recordError("无法读取屏幕画面，请结束后重新授权。"); finishRecording("interrupted"); }
        } finally { if (image != null) image.close(); }
    }
    private String selectText(Text text) {
        if (!largeOnly) return text.getText();
        int tallest = 0;
        for (Text.TextBlock block : text.getTextBlocks()) for (Text.Line line : block.getLines())
            if (line.getBoundingBox() != null && !WordCounter.tokenize(line.getText()).isEmpty()) tallest = Math.max(tallest, line.getBoundingBox().height());
        StringBuilder result = new StringBuilder();
        for (Text.TextBlock block : text.getTextBlocks()) for (Text.Line line : block.getLines())
            if (line.getBoundingBox() != null && line.getBoundingBox().height() >= tallest * .78f) result.append(line.getText()).append('\n');
        return result.toString();
    }
    private SessionStore.Session copySession() {
        SessionStore.Session s = new SessionStore.Session(); s.id = session.id; s.started = session.started;
        s.ended = session.ended; s.status = session.status; s.words = new LinkedHashMap<>(session.words); return s;
    }
    private void checkpoint() {
        SessionStore.Session s = copySession();
        disk.execute(() -> { try { store.save(s); } catch (Exception e) { main.post(() -> { recordError("保存失败，请检查设备剩余空间。"); finishRecording("interrupted"); }); } });
    }
    private void finishRecording(String status) {
        if (stopping) return; stopping = true; active = false; paused = false; saving = session != null; message = "正在保存";
        releaseCapture();
        if (session == null) { saving = false; message = "准备开始"; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return; }
        SessionStore.Session finished = copySession();
        disk.execute(() -> {
            boolean success = true;
            try { store.finish(finished, status); } catch (Exception e) { success = false; recordError("文件保存失败，请检查剩余空间，然后在历史记录中重试导出。"); }
            final boolean saved = success;
            main.post(() -> {
                saving = false; message = saved ? "已保存 " + finished.words.size() + " 词" : "保存失败";
                stopForeground(STOP_FOREGROUND_REMOVE);
                Notification done = new NotificationCompat.Builder(this, "saved").setSmallIcon(R.drawable.ic_record)
                    .setContentTitle(saved ? "单词记录已保存" : "记录保存遇到问题")
                    .setContentText(saved ? "共 " + finished.words.size() + " 个单词 · 点击查看和导出" : "请打开 WordTrace 查看详情")
                    .setContentIntent(openApp()).setAutoCancel(true).build();
                try { getSystemService(NotificationManager.class).notify(2, done); } catch (SecurityException ignored) {}
                stopSelf();
            });
        });
    }
    private void releaseCapture() {
        main.removeCallbacks(sample);
        if (display != null) { display.release(); display = null; }
        if (reader != null) { reader.close(); reader = null; }
        if (projection != null) { projection.unregisterCallback(callback); projection.stop(); projection = null; }
        if (recognizer != null) { recognizer.close(); recognizer = null; }
    }
    private void recordError(String error) { getSharedPreferences("settings", MODE_PRIVATE).edit().putString("error", error).apply(); }
    private PendingIntent openApp() { return PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT); }
    private Notification notification(String detail) {
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, CaptureService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pause = PendingIntent.getService(this, 2, new Intent(this, CaptureService.class).setAction(PAUSE), PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, "recording").setSmallIcon(R.drawable.ic_record)
            .setContentTitle(paused ? "WordTrace · 已暂停" : "WordTrace · 正在记录单词")
            .setContentText(detail).setContentIntent(openApp()).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0, paused ? "继续" : "暂停", pause).addAction(0, "结束并保存", stop).build();
    }
    private void updateNotification() {
        try { getSystemService(NotificationManager.class).notify(1, notification("已记录 " + unique + " 个单词 · " + total + " 次出现")); }
        catch (SecurityException ignored) { /* The visible overlay still offers stop controls. */ }
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        if (Build.VERSION.SDK_INT < 34 && display != null) {
            DisplayMetrics metrics = new DisplayMetrics(); getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(metrics); resize(metrics.widthPixels, metrics.heightPixels);
        }
    }
    @Override public void onDestroy() {
        if (!stopping) finishRecording("interrupted");
        try { unregisterReceiver(screenOff); } catch (IllegalArgumentException ignored) {}
        disk.shutdown(); super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
