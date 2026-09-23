package io.github.wordtrace;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.hardware.display.*;
import android.media.ImageReader;
import android.media.projection.*;
import android.os.*;

/** Not a production recorder: holds a real projection and reports whether it was stopped. */
public class ProjectionFixtureService extends Service {
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private void state(String value) { getSharedPreferences("projection_test", MODE_PRIVATE).edit().putString("state", value).apply(); }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("projection_test", "Projection regression fixture", NotificationManager.IMPORTANCE_LOW));
        Notification notification = new Notification.Builder(this, "projection_test").setSmallIcon(R.drawable.ic_record).setContentTitle("Projection test running").build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(77, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(77, notification);
        projection = getSystemService(MediaProjectionManager.class).getMediaProjection(intent.getIntExtra("code", -1), intent.getParcelableExtra("data"));
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { state("interrupted"); stopSelf(); }
        }, new Handler(getMainLooper()));
        reader = ImageReader.newInstance(320, 180, PixelFormat.RGBA_8888, 2);
        reader.setOnImageAvailableListener(r -> { try (android.media.Image frame = r.acquireLatestImage()) {} }, new Handler(getMainLooper()));
        display = projection.createVirtualDisplay("WordTrace regression recorder", 320, 180, 160, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, null);
        state("running");
        return START_NOT_STICKY;
    }
    @Override public void onDestroy() {
        if (display != null) display.release();
        if (reader != null) reader.close();
        if (projection != null) projection.stop();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
