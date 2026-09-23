package io.github.wordtrace;

import android.content.Context;
import android.os.Build;

final class CaptureMode {
    private CaptureMode() {}
    static boolean compatible(Context context) {
        return Build.VERSION.SDK_INT >= 30 && context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("compatible", true);
    }
}
