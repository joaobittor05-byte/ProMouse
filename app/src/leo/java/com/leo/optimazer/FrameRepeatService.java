package com.leo.optimazer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;

/** Serviço independente do Frame Repeat. */
public class FrameRepeatService extends Service {
    private static final String CHANNEL_ID = "leo_frame_repeat";
    private static final int NOTIFICATION_ID = 4117;

    private HandlerThread thread;
    private Handler worker;
    private String activePackage;
    private String activeMode = "OFF";
    private String activeVsync = FrameRepeatPrefs.VSYNC_AUTO;
    private int activeFps = -1;
    private long nextVerifyAt;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, notification("Frame Repeat aguardando jogo…"));
        ShizukuCore.bindUserService();
        thread = new HandlerThread("Leo-FrameRepeat");
        thread.start();
        worker = new Handler(thread.getLooper());
        worker.post(tick);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            try { sync(); }
            catch (Throwable t) { updateNotification("Frame Repeat limitado • " + safe(t)); }
            if (worker != null) worker.postDelayed(this, 650L);
        }
    };

    private void sync() throws Exception {
        if (!ShizukuCore.hasPermission() || !ShizukuCore.isBinderAlive()) {
            resetActive();
            updateNotification("Frame Repeat • Shizuku indisponível");
            return;
        }

        String top = ShizukuCore.execute("leo top").trim();
        if (top.isEmpty()) return;
        if (activePackage != null && !activePackage.equals(top)) resetActive();

        ProfileStore.Profile profile = ProfileStore.get(this, top);
        boolean hasProfile = profile != null && profile.enabled;
        boolean enabled = hasProfile && FrameRepeatPrefs.isEnabled(this, top, true);
        if (!enabled) {
            if (top.equals(activePackage)) resetActive();
            return;
        }

        String mode = FrameRepeatPrefs.mode(this, top);
        String vsync = FrameRepeatPrefs.vsync(this, top);
        int fps = profile.targetFps;
        long now = SystemClock.elapsedRealtime();
        boolean changed = !top.equals(activePackage)
                || !mode.equals(activeMode)
                || !vsync.equals(activeVsync)
                || fps != activeFps;

        if (changed || now >= nextVerifyAt) {
            String command = "leo frame-apply " + top + " " + fps + " " + mode + " " + vsync;
            String result = ShizukuCore.execute(command);
            activePackage = top;
            activeMode = mode;
            activeVsync = vsync;
            activeFps = fps;
            nextVerifyAt = now + 7000L;
            updateNotification("Frame Repeat • " + FrameRepeatPrefs.friendlyName(mode)
                    + " • VSync " + FrameRepeatPrefs.friendlyVsync(vsync)
                    + " • " + summarize(result));
        }
    }

    private String summarize(String result) {
        String refresh = token(result, "refresh");
        String fps = token(result, "fps");
        String repeat = token(result, "repeat");
        String cadence = token(result, "cadence");
        String vsync = token(result, "vsync");
        StringBuilder b = new StringBuilder();
        if (!fps.isEmpty()) b.append(fps).append(" FPS");
        if (!refresh.isEmpty()) b.append(b.length() == 0 ? "" : " → ").append(refresh).append(" Hz");
        if (!repeat.isEmpty()) b.append(" • ").append(repeat);
        if (!cadence.isEmpty()) b.append(" • ").append(cadence);
        if (!vsync.isEmpty()) b.append(" • vsync=").append(vsync);
        return b.length() == 0 ? "ativo" : b.toString();
    }

    private String token(String text, String key) {
        if (text == null) return "";
        String prefix = key + "=";
        for (String part : text.replace('\n', ' ').split("\\s+")) {
            if (part.startsWith(prefix)) return part.substring(prefix.length());
        }
        return "";
    }

    private void resetActive() {
        if (activePackage != null) {
            try { ShizukuCore.execute("leo frame-reset " + activePackage); } catch (Throwable ignored) {}
        }
        activePackage = null;
        activeMode = "OFF";
        activeVsync = FrameRepeatPrefs.VSYNC_AUTO;
        activeFps = -1;
        nextVerifyAt = 0L;
    }

    @Override public void onDestroy() {
        resetActive();
        if (worker != null) worker.removeCallbacksAndMessages(null);
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, "Leo Frame Repeat", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Frame Repeat universal por aplicativo");
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    private Notification notification(String text) {
        Intent open = new Intent(this, FrameRepeatActivity.class);
        PendingIntent p = PendingIntent.getActivity(this, 4117, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return b.setContentTitle("Leo Optimazer • Frame Repeat")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(p)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager n = getSystemService(NotificationManager.class);
        if (n != null) n.notify(NOTIFICATION_ID, notification(text));
    }

    private String safe(Throwable t) {
        String m = t.getMessage();
        return m == null || m.trim().isEmpty() ? t.getClass().getSimpleName() : m;
    }
}
