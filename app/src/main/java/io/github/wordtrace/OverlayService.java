package io.github.wordtrace;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.pm.ServiceInfo;
import android.content.*;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.core.content.ContextCompat;

/** A visible overlay starts consent; capture itself is owned by a foreground service. */
public class OverlayService extends Service {
    public static volatile boolean running;
    private WindowManager windows;
    private LinearLayout bar;
    private DragLabel state;
    private Button toggle, end;
    private WindowManager.LayoutParams params;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable update = new Runnable() {
        @Override public void run() {
            if (bar == null) return;
            state.setText(getString(R.string.overlay_state, CaptureService.message));
            state.setContentDescription(CaptureService.message + "，轻触切换停靠边，按住可拖动");
            toggle.setText(CaptureService.active ? (CaptureService.paused ? "继续" : "暂停") : "开始");
            toggle.setEnabled(!CaptureService.saving);
            end.setText(CaptureService.active ? "结束" : "关闭"); end.setEnabled(!CaptureService.saving);
            main.postDelayed(this, 700);
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("overlay", "悬浮窗控制", NotificationManager.IMPORTANCE_LOW));
        PendingIntent home = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent close = PendingIntent.getService(this, 3, new Intent(this, OverlayService.class).setAction("close"), PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new androidx.core.app.NotificationCompat.Builder(this, "overlay").setSmallIcon(R.drawable.ic_record)
            .setContentTitle("WordTrace 悬浮窗已开启").setContentText("通过悬浮窗开始或结束；点击返回应用。")
            .setContentIntent(home).setOngoing(true).addAction(0, "关闭并保存", close).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(3, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(3, notification);
        windows = getSystemService(WindowManager.class);
        bar = new LinearLayout(this); bar.setOrientation(LinearLayout.VERTICAL); bar.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(ContextCompat.getColor(this, R.color.primary_container)); bg.setCornerRadius(dp(16));
        bar.setBackground(bg); bar.setElevation(dp(4));
        state = new DragLabel(this); state.setTextColor(ContextCompat.getColor(this, R.color.ink)); state.setTextSize(12); state.setGravity(Gravity.CENTER); state.setMinHeight(dp(48));
        state.setContentDescription("记录状态，按住可拖动悬浮窗"); bar.addView(state);
        toggle = button("开始"); end = button("关闭");
        bar.addView(toggle, new LinearLayout.LayoutParams(dp(96), dp(48)));
        bar.addView(end, new LinearLayout.LayoutParams(dp(96), dp(48)));
        params = new WindowManager.LayoutParams(dp(112), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START; params.x = dp(12); params.y = dp(120);
        state.setOnClickListener(v -> {
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics(); windows.getDefaultDisplay().getRealMetrics(metrics);
            params.x = params.x < metrics.widthPixels / 2 ? metrics.widthPixels - dp(124) : dp(12);
            clamp(); windows.updateViewLayout(bar, params);
        });
        state.setOnTouchListener(new View.OnTouchListener() {
            private float sx, sy; private int ox, oy;
            @Override public boolean onTouch(View v, android.view.MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) { sx = event.getRawX(); sy = event.getRawY(); ox = params.x; oy = params.y; return true; }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    params.x = ox + (int)(event.getRawX() - sx); params.y = oy + (int)(event.getRawY() - sy); clamp();
                    try { windows.updateViewLayout(bar, params); } catch (IllegalArgumentException ignored) {} return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) { if (Math.abs(event.getRawX() - sx) + Math.abs(event.getRawY() - sy) < dp(8)) v.performClick(); return true; }
                return false;
            }
        });
        toggle.setOnClickListener(v -> {
            if (CaptureService.active) startService(new Intent(this, CaptureService.class).setAction(CaptureService.PAUSE));
            else if (!CaptureService.saving) startActivity(new Intent(this, CaptureConsentActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });
        end.setOnClickListener(v -> {
            if (CaptureService.active) startService(new Intent(this, CaptureService.class).setAction(CaptureService.STOP));
            stopSelf();
        });
        try { windows.addView(bar, params); running = true; main.post(update); }
        catch (RuntimeException e) { bar = null; stopSelf(); }
    }
    private void clamp() {
        android.util.DisplayMetrics m = new android.util.DisplayMetrics(); windows.getDefaultDisplay().getRealMetrics(m);
        params.x = Math.max(0, Math.min(params.x, m.widthPixels - dp(112)));
        params.y = Math.max(0, Math.min(params.y, m.heightPixels - dp(200)));
    }
    private Button button(String label) {
        Button b = new Button(this); b.setText(label); b.setTextSize(14); b.setAllCaps(false); b.setTextColor(ContextCompat.getColor(this, R.color.ink));
        return b;
    }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override public int onStartCommand(Intent i, int f, int id) {
        if (i != null && "close".equals(i.getAction())) {
            if (CaptureService.active) startService(new Intent(this, CaptureService.class).setAction(CaptureService.STOP));
            stopSelf();
        }
        return START_NOT_STICKY;
    }
    private static final class DragLabel extends androidx.appcompat.widget.AppCompatTextView {
        DragLabel(Context context) { super(context); }
        @Override public boolean performClick() { super.performClick(); return true; }
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config); if (bar != null) { clamp(); windows.updateViewLayout(bar, params); }
    }
    @Override public void onDestroy() {
        running = false; main.removeCallbacks(update);
        if (bar != null) { try { windows.removeView(bar); } catch (IllegalArgumentException ignored) {} bar = null; }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
