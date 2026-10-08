package io.github.wordtrace;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import androidx.core.content.ContextCompat;

public class CaptureConsentActivity extends Activity {
    public static volatile boolean visible;
    private boolean projectionPending;
    @Override protected void onResume() { super.onResume(); visible = true; }
    @Override protected void onPause() { visible = false; super.onPause(); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (CaptureService.active || CaptureService.saving) { finish(); return; }
        if (state != null && state.getBoolean("projectionPending")) { projectionPending = true; return; }
        if (getSharedPreferences("settings", MODE_PRIVATE).getBoolean("captureExplained", false)) { requestCapture(); return; }
        new AlertDialog.Builder(this).setTitle("开始识别单词")
            .setMessage("请在接下来的系统提示中允许屏幕共享，支持时选择不背单词。画面只在本机识别，不保存截图。\n\n请先结束系统录屏：这两个功能使用同一采集通道，不能保证同时运行。")
            .setNegativeButton("取消", (d,w) -> finish()).setOnCancelListener(d -> finish())
            .setPositiveButton("继续", (d,w) -> {
                getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("captureExplained", true).apply();
                requestCapture();
            }).show();
    }
    private void requestCapture() {
        try { projectionPending = true; startActivityForResult(getSystemService(MediaProjectionManager.class).createScreenCaptureIntent(), 42); }
        catch (RuntimeException e) { report("无法发起屏幕共享，请检查系统限制。"); finish(); }
    }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putBoolean("projectionPending", projectionPending); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data); projectionPending = false;
        if (request == 42 && result == RESULT_OK && data != null) {
            try { ContextCompat.startForegroundService(this, new Intent(this, CaptureService.class).putExtra("code", result).putExtra("data", data)); }
            catch (RuntimeException e) { report("无法开始记录，请回到 WordTrace 重试。"); }
        }
        finish();
    }
    private void report(String message) {
        getSharedPreferences("settings", MODE_PRIVATE).edit().putString("error", message).apply();
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
    }
}
