package io.github.wordtrace;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;

/** Debug-only holder of an independent projection, to detect recorder interruption. */
public class ProjectionFixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state == null) startActivityForResult(getSystemService(MediaProjectionManager.class).createScreenCaptureIntent(), 77);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 77 && result == RESULT_OK && data != null)
            startForegroundService(new Intent(this, ProjectionFixtureService.class).putExtra("data", data).putExtra("code", result));
        finish();
    }
}
