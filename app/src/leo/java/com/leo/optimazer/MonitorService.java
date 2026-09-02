package com.leo.optimazer;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;

public class MonitorService extends Service {
    public static final String PREFS = "leo_settings";
    public static final String KEY_ENABLED = "monitor_enabled";
    public static final String KEY_INTERVAL_SEC = "ram_interval_sec";
    public static final String KEY_LAST_CLEANUP = "last_cleanup";
    public static final String KEY_LAST_FREED_MB = "last_freed_mb";
    public static final String KEY_LAST_CLEANUP_RESULT = "last_cleanup_result";
    public static final String KEY_LAST_PROFILE_RESULT = "last_profile_result";

    private static final String CHANNEL_ID = "leo_optimizer_core";
    private static final int NOTIFICATION_ID = 4107;

    private HandlerThread workerThread;
    private Handler worker;
    private long nextCleanupAt = Long.MAX_VALUE;
    private String activeDpiPackage;
    private int activeTaskDensity = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, notification("Núcleo Shizuku inicializando…"));
        ShizukuCore.bindUserService();
        workerThread = new HandlerThread("LeoOptimazer-CoreMonitor");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        scheduleFromPreferences();
        worker.post(tick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, true).apply();
        scheduleFromPreferences();
        return START_STICKY;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long now = SystemClock.elapsedRealtime();
            long sec = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_INTERVAL_SEC, 0L);
            String profileText = "Perfis aguardando app";

            if (!ShizukuCore.hasPermission() || !ShizukuCore.isBinderAlive()) {
                updateNotification("Shizuku indisponível • abra o Leo Optimazer");
            } else {
                ShizukuCore.bindUserService();
                try {
                    profileText = syncForegroundProfile();
                } catch (Exception e) {
                    profileText = "DPI por app indisponível: " + safeMessage(e);
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString(KEY_LAST_PROFILE_RESULT, profileText)
                            .apply();
                }

                if (sec < 10L) {
                    nextCleanupAt = Long.MAX_VALUE;
                    updateNotification(profileText + " • RAM auto off");
                } else if (now >= nextCleanupAt) {
                    runAutomaticCleanup();
                    nextCleanupAt = SystemClock.elapsedRealtime() + sec * 1000L;
                } else {
                    long remaining = Math.max(0L, (nextCleanupAt - now + 999L) / 1000L);
                    updateNotification(profileText + " • RAM em " + formatRemaining(remaining));
                }
            }

            if (worker != null) worker.postDelayed(this, 900L);
        }
    };

    private String syncForegroundProfile() throws Exception {
        String top = ShizukuCore.execute("leo top").trim();
        if (top.isEmpty()) return "Nenhum app detectado";

        if (activeDpiPackage != null && !activeDpiPackage.equals(top)) {
            try { ShizukuCore.execute("leo density-reset " + activeDpiPackage); } catch (Exception ignored) {}
            activeDpiPackage = null;
            activeTaskDensity = -1;
        }

        ProfileStore.Profile profile = ProfileStore.get(this, top);
        if (profile == null || !profile.enabled) {
            return "Sistema intacto • " + shortName(top);
        }

        PerAppCompat.Plan plan = PerAppCompat.build(profile, getResources().getDisplayMetrics());
        int wantedDensity = plan.estimatedDensity;
        if (!top.equals(activeDpiPackage) || wantedDensity != activeTaskDensity) {
            String result = ShizukuCore.execute("leo density " + top + " " + wantedDensity);
            if (result.startsWith("TASK_DENSITY_OK") || result.startsWith("TASK_DENSITY_ALREADY")) {
                activeDpiPackage = top;
                activeTaskDensity = wantedDensity;
                String state = "Perfil " + shortName(top) + " • DPI Virtual " + plan.normalizedDensity;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString(KEY_LAST_PROFILE_RESULT, state + " • Android " + wantedDensity)
                        .apply();
                return state;
            }
            return "Perfil salvo • aguardando tarefa " + shortName(top);
        }

        return "Perfil " + shortName(top) + " • DPI Virtual " + plan.normalizedDensity;
    }

    private void runAutomaticCleanup() {
        long before = availableMemoryMb();
        try {
            ShizukuCore.execute("am kill-all");
            SystemClock.sleep(450L);
            long after = availableMemoryMb();
            long freed = Math.max(0L, after - before);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putLong(KEY_LAST_CLEANUP, System.currentTimeMillis())
                    .putLong(KEY_LAST_FREED_MB, freed)
                    .putString(KEY_LAST_CLEANUP_RESULT, "SHIZUKU_OK")
                    .apply();
            updateNotification("RAM limpa • +" + freed + " MB • perfil por app continua ativo");
        } catch (Exception e) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putLong(KEY_LAST_CLEANUP, System.currentTimeMillis())
                    .putString(KEY_LAST_CLEANUP_RESULT, "ERRO: " + safeMessage(e))
                    .apply();
            updateNotification("Falha na limpeza • núcleo Shizuku indisponível");
        }
    }

    private synchronized void scheduleFromPreferences() {
        long sec = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_INTERVAL_SEC, 0L);
        nextCleanupAt = sec < 10L ? Long.MAX_VALUE : SystemClock.elapsedRealtime() + sec * 1000L;
    }

    private long availableMemoryMb() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        return info.availMem / (1024L * 1024L);
    }

    private String shortName(String packageName) {
        int dot = packageName.lastIndexOf('.');
        return dot >= 0 && dot + 1 < packageName.length() ? packageName.substring(dot + 1) : packageName;
    }

    private String formatRemaining(long seconds) {
        if (seconds >= 3600L) return (seconds / 3600L) + "h " + ((seconds % 3600L) / 60L) + "min";
        if (seconds >= 60L) return (seconds / 60L) + "min " + (seconds % 60L) + "s";
        return seconds + "s";
    }

    private String safeMessage(Throwable t) {
        String message = t.getMessage();
        return message == null || message.trim().isEmpty() ? t.getClass().getSimpleName() : message;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Leo Optimazer", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Perfis individuais de DPI/resolução e limpeza automática via Shizuku");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification notification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(
                this,
                4107,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("Leo Optimazer • Shizuku")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(text));
    }

    @Override
    public void onDestroy() {
        if (activeDpiPackage != null && ShizukuCore.isReady()) {
            try { ShizukuCore.execute("leo density-reset " + activeDpiPackage); } catch (Exception ignored) {}
        }
        activeDpiPackage = null;
        activeTaskDensity = -1;
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
