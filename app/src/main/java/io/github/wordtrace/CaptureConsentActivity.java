package io.github.wordtrace;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.provider.Settings;
import androidx.core.content.ContextCompat;

/** No automatic fallback: compatibility mode never opens MediaProjection. */
public class CaptureConsentActivity extends Activity {
    public static volatile boolean visible;
    private boolean projectionPending;
    @Override protected void onResume() { super.onResume(); visible = true; }
    @Override protected void onPause() { visible = false; super.onPause(); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (CaptureService.active || CaptureService.saving) { finish(); return; }
        if (state == null || !state.getBoolean("projectionPending")) chooseSource();
        else projectionPending = true;
    }
    private void chooseSource() {
        if (CaptureMode.compatible(this)) {
            boolean consent = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("compatibleDisclosure", false);
            if (!consent || !ScreenReaderService.available()) {
                new AlertDialog.Builder(this).setTitle("开启录屏兼容识别")
                    .setMessage(getString(R.string.accessibility_description) + "\n\n请在无障碍 / 辅助功能中开启「WordTrace 录屏兼容识别」，然后回到学习页面，再点悬浮球开始。如提示受限设置，请在系统的 WordTrace 应用信息页按提示允许后再开启。未开启时不会自动切换为屏幕共享。")
                    .setNegativeButton("取消", (d,w) -> finish()).setOnCancelListener(d -> finish())
                    .setPositiveButton(ScreenReaderService.available() ? "同意并开始" : "同意，去开启", (d,w) -> {
                        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("compatibleDisclosure", true).apply();
                        if (ScreenReaderService.available()) startCompatible();
                        else {
                            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
                            catch (RuntimeException e) { report("请在系统设置 → 无障碍 / 辅助功能中开启 WordTrace 服务。"); }
                            finish();
                        }
                    }).show();
            } else startCompatible();
        } else {
            new AlertDialog.Builder(this).setTitle("屏幕共享可能结束系统录屏")
                .setMessage("此模式与系统录屏共用屏幕采集通道，授权后可能中断正在进行的录屏。需要同时录屏时，请取消，并在 WordTrace 中选择录屏兼容模式（Android 11 及以上）。")
                .setNegativeButton("取消", (d,w) -> finish()).setOnCancelListener(d -> finish())
                .setPositiveButton("继续屏幕共享", (d,w) -> {
                    try { projectionPending = true; startActivityForResult(getSystemService(MediaProjectionManager.class).createScreenCaptureIntent(), 42); }
                    catch (RuntimeException e) { report("此设备无法发起屏幕共享，请检查系统限制。"); finish(); }
                }).show();
        }
    }
    private void startCompatible() { startCapture(new Intent(this, CaptureService.class).putExtra("compatible", true)); }
    private void startCapture(Intent intent) {
        try { ContextCompat.startForegroundService(this, intent); }
        catch (RuntimeException e) { report("无法开始记录，请回到 WordTrace 重新打开悬浮球。"); }
        finish();
    }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putBoolean("projectionPending", projectionPending); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data); projectionPending = false;
        if (request == 42 && result == RESULT_OK && data != null)
            startCapture(new Intent(this, CaptureService.class).putExtra("code", result).putExtra("data", data));
        else finish();
    }
    private void report(String message) {
        getSharedPreferences("settings", MODE_PRIVATE).edit().putString("error", message).apply();
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
    }
}
