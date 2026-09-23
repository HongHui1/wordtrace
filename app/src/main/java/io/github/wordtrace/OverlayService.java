package io.github.wordtrace;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;

/** A 36dp visible ball in a 48dp touch target; controls appear only on demand. */
public class OverlayService extends Service {
    public static volatile boolean running;
    private WindowManager windows;
    private LinearLayout root;
    private Bubble bubble;
    private TextView status;
    private MaterialButton toggle, end;
    private WindowManager.LayoutParams params;
    private boolean expanded, attached;
    private int collapsedX, collapsedY;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable collapse = () -> setExpanded(false);
    private final Runnable update = new Runnable() {
        @Override public void run() {
            if (root == null) return;
            bubble.setContentDescription("WordTrace，" + CaptureService.message + "。轻触展开操作，按住拖动");
            bubble.invalidate();
            if (expanded) {
                status.setText(CaptureService.message);
                toggle.setText(CaptureService.active ? (CaptureService.paused ? "继续" : "暂停") : "开始");
                toggle.setEnabled(!CaptureService.saving);
                end.setText(CaptureService.active ? "结束并保存" : "关闭悬浮球");
                end.setEnabled(!CaptureService.saving);
            }
            main.postDelayed(this, 700);
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("overlay", "悬浮球控制", NotificationManager.IMPORTANCE_LOW));
        PendingIntent home = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent close = PendingIntent.getService(this, 3, new Intent(this, OverlayService.class).setAction("close"), PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new androidx.core.app.NotificationCompat.Builder(this, "overlay").setSmallIcon(R.drawable.ic_record)
            .setContentTitle("WordTrace 悬浮球已开启").setContentText("轻触小球展开；按住可拖动。")
            .setContentIntent(home).setOngoing(true).addAction(0, "关闭并保存", close).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(3, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(3, notification);
        windows = getSystemService(WindowManager.class);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        params = new WindowManager.LayoutParams(dp(48), dp(48), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START; params.x = dp(8); params.y = dp(120);
        setExpanded(false);
        try { windows.addView(root, params); attached = true; running = true; main.post(update); }
        catch (RuntimeException e) { root = null; stopSelf(); }
    }
    private void setExpanded(boolean show) {
        if (root == null) return;
        main.removeCallbacks(collapse);
        if (show && !expanded) { collapsedX = params.x; collapsedY = params.y; }
        if (!show && expanded) { params.x = collapsedX; params.y = collapsedY; }
        expanded = show; root.removeAllViews(); root.setPadding(0,0,0,0);
        params.width = dp(show ? 176 : 48); params.height = show ? WindowManager.LayoutParams.WRAP_CONTENT : dp(48);
        if (show) {
            GradientDrawable bg = new GradientDrawable(); bg.setColor(color(R.color.primary_container)); bg.setCornerRadius(dp(16)); root.setBackground(bg);
        } else root.setBackgroundColor(Color.TRANSPARENT);
        bubble = new Bubble(); root.addView(bubble, new LinearLayout.LayoutParams(dp(48), dp(48)));
        bubble.setOnClickListener(v -> setExpanded(!expanded));
        if (show) {
            status = new TextView(this); status.setText(CaptureService.message); status.setTextSize(13); status.setTextColor(color(R.color.ink)); status.setPadding(dp(12),0,dp(12),dp(4)); root.addView(status);
            toggle = button(CaptureService.active ? (CaptureService.paused ? "继续" : "暂停") : "开始");
            toggle.setOnClickListener(v -> {
                if (CaptureService.active) startService(new Intent(this, CaptureService.class).setAction(CaptureService.PAUSE));
                else if (!CaptureService.saving) {
                    try { startActivity(new Intent(this, CaptureConsentActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                    catch (RuntimeException e) { Toast.makeText(this, "请回到 WordTrace 重新打开悬浮球", Toast.LENGTH_LONG).show(); }
                }
                setExpanded(false);
            });
            end = button(CaptureService.active ? "结束并保存" : "关闭悬浮球"); end.setOnClickListener(v -> close());
            toggle.setEnabled(!CaptureService.saving); end.setEnabled(!CaptureService.saving);
            button("收起").setOnClickListener(v -> setExpanded(false));
            main.postDelayed(collapse, 8000);
        }
        clamp(); if (attached) windows.updateViewLayout(root, params);
    }
    private MaterialButton button(String label) {
        Context theme = new androidx.appcompat.view.ContextThemeWrapper(this, R.style.Theme_WordTrace);
        MaterialButton b = new MaterialButton(theme, null, com.google.android.material.R.attr.borderlessButtonStyle);
        b.setText(label); b.setTextSize(14); b.setAllCaps(false); b.setMinHeight(dp(48));
        root.addView(b, new LinearLayout.LayoutParams(-1, -2)); return b;
    }
    private void close() {
        if (CaptureService.active) startService(new Intent(this, CaptureService.class).setAction(CaptureService.STOP));
        stopSelf();
    }
    private void clamp() {
        android.util.DisplayMetrics m = new android.util.DisplayMetrics(); windows.getDefaultDisplay().getRealMetrics(m);
        params.x = Math.max(0, Math.min(params.x, m.widthPixels - params.width));
        params.y = Math.max(0, Math.min(params.y, m.heightPixels - dp(expanded ? 256 : 72)));
    }
    private final class Bubble extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path play = new Path();
        private float sx, sy; private int ox, oy; private boolean moved;
        Bubble() { super(OverlayService.this); setFocusable(true); setClickable(true); setContentDescription("WordTrace，轻触展开，按住拖动"); play.moveTo(dp(21),dp(17)); play.lineTo(dp(31),dp(24)); play.lineTo(dp(21),dp(31)); play.close(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setColor(color(R.color.primary)); canvas.drawCircle(dp(24),dp(24),dp(18),paint);
            paint.setColor(color(R.color.on_primary));
            if (CaptureService.saving) {
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); canvas.drawCircle(dp(24),dp(24),dp(7),paint); paint.setStyle(Paint.Style.FILL);
            } else if (CaptureService.active && CaptureService.paused) {
                canvas.drawRect(dp(18),dp(17),dp(22),dp(31),paint); canvas.drawRect(dp(26),dp(17),dp(30),dp(31),paint);
            } else if (CaptureService.active) canvas.drawCircle(dp(24),dp(24),dp(6),paint);
            else canvas.drawPath(play,paint);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            switch(e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: sx=e.getRawX(); sy=e.getRawY(); ox=params.x; oy=params.y; moved=false; return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(e.getRawX()-sx)+Math.abs(e.getRawY()-sy)>ViewConfiguration.get(getContext()).getScaledTouchSlop()) moved=true;
                    if (moved) { params.x=ox+(int)(e.getRawX()-sx); params.y=oy+(int)(e.getRawY()-sy); clamp(); windows.updateViewLayout(root,params); if(expanded) { collapsedX=params.x; collapsedY=params.y; } }
                    return true;
                case MotionEvent.ACTION_UP: if (!moved) performClick(); return true;
                case MotionEvent.ACTION_CANCEL: return true;
                default: return super.onTouchEvent(e);
            }
        }
        @Override public boolean performClick() { super.performClick(); return true; }
    }
    private int color(int id) { return ContextCompat.getColor(this,id); }
    private int dp(float n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    @Override public int onStartCommand(Intent i,int f,int id) { if(i!=null && "close".equals(i.getAction())) close(); return START_NOT_STICKY; }
    @Override public void onConfigurationChanged(android.content.res.Configuration config) { super.onConfigurationChanged(config); if(root!=null) setExpanded(false); }
    @Override public void onDestroy() {
        running=false; main.removeCallbacksAndMessages(null);
        if(root!=null && attached) { try { windows.removeView(root); } catch(IllegalArgumentException ignored) {} } root=null; attached=false;
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
