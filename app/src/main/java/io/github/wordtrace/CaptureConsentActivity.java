package io.github.wordtrace;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import androidx.core.content.ContextCompat;

/** Every recording obtains fresh, explicit OS consent; tokens are never reused. */
public class CaptureConsentActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (CaptureService.active) { finish(); return; }
        if (state == null) {
            try { startActivityForResult(getSystemService(MediaProjectionManager.class).createScreenCaptureIntent(), 42); }
            catch (RuntimeException e) { report("此设备无法发起屏幕共享，请在系统设置中检查限制。"); finish(); }
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 42 && result == RESULT_OK && data != null) {
            Intent service = new Intent(this, CaptureService.class).putExtra("code", result).putExtra("data", data);
            try { ContextCompat.startForegroundService(this, service); }
            catch (RuntimeException e) { report("无法启动录制，请回到 WordTrace 重新打开悬浮窗。"); }
        }
        finish();
    }
    private void report(String message) {
        getSharedPreferences("settings", MODE_PRIVATE).edit().putString("error", message).apply();
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
    }
}
