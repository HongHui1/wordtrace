package io.github.wordtrace;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.RequiresApi;

/** Screenshot-only bridge: no window tree, text events, gestures or MediaProjection. */
public class ScreenReaderService extends AccessibilityService {
    private static ScreenReaderService connected;
    interface FrameCallback { void success(Bitmap bitmap); void failure(int code); }
    static boolean available() { return connected != null; }
    @Override protected void onServiceConnected() { connected = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { /* No events requested. */ }
    @Override public void onInterrupt() { /* Recording has an explicit stop control. */ }
    @Override public boolean onUnbind(Intent intent) { if (connected == this) connected = null; return super.onUnbind(intent); }
    @Override public void onDestroy() { if (connected == this) connected = null; super.onDestroy(); }
    @RequiresApi(30)
    static void requestFrame(FrameCallback callback) {
        ScreenReaderService service = connected;
        if (service == null) { callback.failure(-1); return; }
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, service.getMainExecutor(), new TakeScreenshotCallback() {
                @Override public void onSuccess(ScreenshotResult result) {
                    Bitmap software = null;
                    try (HardwareBuffer buffer = result.getHardwareBuffer()) {
                        Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                        if (hardware != null) {
                            try { software = hardware.copy(Bitmap.Config.ARGB_8888, false); }
                            finally { hardware.recycle(); }
                        }
                    } catch (RuntimeException ignored) {}
                    if (software == null) callback.failure(ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR);
                    else callback.success(software);
                }
                @Override public void onFailure(int errorCode) { callback.failure(errorCode); }
            });
        } catch (RuntimeException e) { callback.failure(-1); }
    }
}
