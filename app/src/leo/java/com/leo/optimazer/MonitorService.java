package com.leo.optimazer;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MonitorService extends Service {
    public static final String PREFS = "leo_settings";
    public static final String KEY_ENABLED = "monitor_enabled";
    public static final String KEY_INTERVAL_SEC = "ram_interval_sec";
    public static final String KEY_LAST_CLEANUP = "last_cleanup";
    public static final String KEY_LAST_FREED_MB = "last_freed_mb";

    private static final String CHANNEL_ID = "leo_optimizer_core";
    private static final int NOTIFICATION_ID = 4107;

    private HandlerThread workerThread;
    private Handler worker;
    private ProfileStore.Profile appliedProfile;
    private String originalSizeOverride;
    private Integer originalDensityOverride;
    private long nextCleanupAt = Long.MAX_VALUE;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, notification("Inicializando…"));
        workerThread = new HandlerThread("LeoOptimazer-Core");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        scheduleCleanupFromPreferences();
        worker.post(tick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, true).apply();
        scheduleCleanupFromPreferences();
        return START_STICKY;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            try {
                String top = BridgeClient.send("TOP").trim();
                handleTopPackage(top);
                maybeCleanRam();
                updateNotification(top);
            } catch (Exception e) {
                updateNotification("Bridge ADB desconectado");
            } finally {
                if (worker != null) worker.postDelayed(this, 1200L);
            }
        }
    };

    private void handleTopPackage(String topPackage) throws Exception {
        if (appliedProfile != null && !appliedProfile.packageName.equals(topPackage)) {
            restoreDisplay();
            appliedProfile = null;
        }

        if (appliedProfile == null && topPackage != null && !topPackage.isEmpty()) {
            ProfileStore.Profile profile = ProfileStore.get(this, topPackage);
            if (profile != null && profile.enabled) {
                applyProfile(profile);
                appliedProfile = profile;
            }
        }
    }

    private void applyProfile(ProfileStore.Profile profile) throws Exception {
        originalSizeOverride = parseOverrideSize(BridgeClient.send("GET_SIZE"));
        originalDensityOverride = parseOverrideDensity(BridgeClient.send("GET_DENSITY"));

        BridgeClient.send("SET_SIZE " + profile.width + " " + profile.height);
        BridgeClient.send("SET_DENSITY " + profile.density);
    }

    private void restoreDisplay() {
        try {
            if (originalSizeOverride == null) {
                BridgeClient.send("RESET_SIZE");
            } else {
                String[] parts = originalSizeOverride.split("x");
                BridgeClient.send("SET_SIZE " + parts[0] + " " + parts[1]);
            }
        } catch (Exception ignored) {}

        try {
            if (originalDensityOverride == null) {
                BridgeClient.send("RESET_DENSITY");
            } else {
                BridgeClient.send("SET_DENSITY " + originalDensityOverride);
            }
        } catch (Exception ignored) {}

        originalSizeOverride = null;
        originalDensityOverride = null;
    }

    private String parseOverrideSize(String output) {
        Matcher m = Pattern.compile("Override size:\\s*(\\d+x\\d+)").matcher(output);
        return m.find() ? m.group(1) : null;
    }

    private Integer parseOverrideDensity(String output) {
        Matcher m = Pattern.compile("Override density:\\s*(\\d+)").matcher(output);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private synchronized void scheduleCleanupFromPreferences() {
        long sec = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_INTERVAL_SEC, 0L);
        if (sec < 10L) {
            nextCleanupAt = Long.MAX_VALUE;
        } else {
            nextCleanupAt = SystemClock.elapsedRealtime() + sec * 1000L;
        }
    }

    private void maybeCleanRam() {
        long now = SystemClock.elapsedRealtime();
        if (now < nextCleanupAt) return;
        long interval = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_INTERVAL_SEC, 0L);
        if (interval < 10L) {
            nextCleanupAt = Long.MAX_VALUE;
            return;
        }
        cleanRamNow();
        nextCleanupAt = now + interval * 1000L;
    }

    public void cleanRamNow() {
        long before = availableMemoryMb();
        try { BridgeClient.send("KILL_CACHED"); } catch (Exception ignored) {}
        SystemClock.sleep(350L);
        long after = availableMemoryMb();
        long freed = Math.max(0L, after - before);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong(KEY_LAST_CLEANUP, System.currentTimeMillis())
                .putLong(KEY_LAST_FREED_MB, freed)
                .apply();
    }

    private long availableMemoryMb() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        return info.availMem / (1024L * 1024L);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Leo Optimazer", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Perfis de resolução/DPI e limpeza programada de RAM");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification notification(String text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setContentTitle("Leo Optimazer ativo")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String topPackage) {
        String text = appliedProfile == null ? "Monitorando • " + topPackage : "Perfil ativo • " + appliedProfile.packageName;
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(text));
    }

    @Override
    public void onDestroy() {
        restoreDisplay();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply();
        if (worker != null) worker.removeCallbacksAndMessages(null);
        if (workerThread != null) workerThread.quitSafely();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
