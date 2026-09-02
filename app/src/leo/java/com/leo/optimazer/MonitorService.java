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
    public static final String KEY_LAST_TOUCH_RESULT = "last_touch_result";

    private static final String CHANNEL_ID = "leo_optimizer_core";
    private static final int NOTIFICATION_ID = 4107;

    private HandlerThread workerThread;
    private Handler worker;
    private long nextCleanupAt = Long.MAX_VALUE;
    private String activeDpiPackage;
    private int activeTaskDensity = -1;
    private int activeDedicatedDpi = -1;
    private long nextDensityVerificationAt = 0L;
    private String activeTouchPackage;
    private String activeTouchSummary = "OFF";
    private long nextTouchRefreshAt = 0L;

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
                    profileText = syncForegroundProfile(now);
                } catch (Exception e) {
                    profileText = "Perfil por app indisponível: " + safeMessage(e);
                    saveProfileState(profileText);
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

            if (worker != null) worker.postDelayed(this, 800L);
        }
    };

    private String syncForegroundProfile(long now) throws Exception {
        String top = ShizukuCore.execute("leo top").trim();
        if (top.isEmpty()) return "Nenhum app detectado";

        if (activeDpiPackage != null && !activeDpiPackage.equals(top)) {
            try { ShizukuCore.execute("leo density-reset " + activeDpiPackage); } catch (Exception ignored) {}
            clearActiveDpi();
        }
        if (activeTouchPackage != null && !activeTouchPackage.equals(top)) {
            try { ShizukuCore.execute("leo touch-reset " + activeTouchPackage); } catch (Exception ignored) {}
            clearActiveTouch();
        }

        ProfileStore.Profile profile = ProfileStore.get(this, top);
        if (profile == null || !profile.enabled) {
            String state = "Sistema intacto • " + shortName(top);
            saveProfileState(state);
            return state;
        }

        PerAppCompat.Plan plan = PerAppCompat.build(profile, getResources().getDisplayMetrics());
        String touchState = syncTouchEngine(top, profile, now);

        int wantedDensity = plan.estimatedDensity;
        boolean needsApply = !top.equals(activeDpiPackage) || wantedDensity != activeTaskDensity;

        if (!needsApply && now >= nextDensityVerificationAt) {
            String status = ShizukuCore.execute("leo density-status " + top);
            if (!isVerified(status, wantedDensity)) {
                needsApply = true;
            } else {
                nextDensityVerificationAt = now + 3000L;
                String state = "DPI dedicada " + plan.normalizedDensity + " verificada • Touch " + touchState;
                saveProfileState(state + "\n" + status);
                return state;
            }
        }

        if (needsApply) {
            String result = ShizukuCore.execute("leo density " + top + " " + wantedDensity);
            if (isSuccess(result, wantedDensity)) {
                activeDpiPackage = top;
                activeTaskDensity = wantedDensity;
                activeDedicatedDpi = plan.normalizedDensity;
                nextDensityVerificationAt = now + 2200L;
                String state = "DPI " + plan.normalizedDensity + " aplicada • Touch " + touchState;
                saveProfileState(state + "\n" + result);
                return state;
            }

            if (result.startsWith("TASK_DENSITY_REJECTED")) {
                clearActiveDpi();
                String state = "Android/ROM rejeitou a DPI • Touch " + touchState;
                saveProfileState(state + "\n" + result);
                return state;
            }

            String state = "Perfil salvo • aguardando tarefa • Touch " + touchState;
            saveProfileState(state + "\n" + result);
            return state;
        }

        return "DPI " + activeDedicatedDpi + " ativa • Touch " + touchState;
    }

    private String syncTouchEngine(String top, ProfileStore.Profile profile, long now) throws Exception {
        if (!profile.fastTouch && !profile.linearDrag) {
            if (top.equals(activeTouchPackage)) {
                ShizukuCore.execute("leo touch-reset " + top);
                clearActiveTouch();
            }
            saveTouchState("desligado");
            return "OFF";
        }

        if (!top.equals(activeTouchPackage) || now >= nextTouchRefreshAt) {
            String command = "leo touch-apply " + top + " "
                    + (profile.fastTouch ? "1" : "0") + " "
                    + (profile.linearDrag ? "1" : "0") + " "
                    + profile.touchLevel + " v1";
            String result = ShizukuCore.execute(command);
            activeTouchPackage = top;
            nextTouchRefreshAt = now + 5000L;
            saveTouchState(result);
            activeTouchSummary = summarizeTouchResult(result, profile.fastTouch, profile.linearDrag);
            return activeTouchSummary;
        }

        return activeTouchSummary;
    }

    private String summarizeTouchResult(String result, boolean fast, boolean linear) {
        boolean universal = result.contains("TOUCH_ENGINE=UNIVERSAL_AOSP");
        boolean smooth = result.contains("LINEAR_DRAG=AOSP_RESAMPLING_ACTIVE");
        boolean romBlocked = result.contains("ROM_DISABLED_AOSP_RESAMPLING");
        boolean fastPipeline = result.contains("FAST_TOUCH=AOSP_PERFORMANCE_PIPELINE");

        if (fastPipeline && smooth && fast && linear) return "AOSP RÁPIDO+SUAVE";
        if (smooth && linear) return "AOSP SUAVE";
        if (fastPipeline && fast) return romBlocked && linear
                ? "AOSP RÁPIDO • ARRASTO LIMITADO PELA ROM"
                : "AOSP RÁPIDO";
        if (romBlocked && linear) return "ARRASTO LIMITADO PELA ROM";
        if (universal) return "AOSP ATIVO";
        return "LIMITADO";
    }

    private boolean isSuccess(String result, int wantedDensity) {
        if (!(result.startsWith("TASK_DENSITY_OK") || result.startsWith("TASK_DENSITY_ALREADY"))) return false;
        return result.contains("actual=" + wantedDensity) && result.contains("verified=true");
    }

    private boolean isVerified(String result, int wantedDensity) {
        return result.startsWith("TASK_DENSITY_STATUS")
                && result.contains("current=" + wantedDensity)
                && result.contains("verified=true");
    }

    private void saveProfileState(String value) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_LAST_PROFILE_RESULT, value).apply();
    }

    private void saveTouchState(String value) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_LAST_TOUCH_RESULT, value).apply();
    }

    private void clearActiveDpi() {
        activeDpiPackage = null;
        activeTaskDensity = -1;
        activeDedicatedDpi = -1;
        nextDensityVerificationAt = 0L;
    }

    private void clearActiveTouch() {
        activeTouchPackage = null;
        activeTouchSummary = "OFF";
        nextTouchRefreshAt = 0L;
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
            updateNotification("RAM limpa • +" + freed + " MB • Touch Engine mantido");
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
            channel.setDescription("Perfis de DPI/resolução, Touch Engine universal e RAM automática via Shizuku");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification notification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 4107, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("Leo Optimazer • Shizuku + Touch AOSP")
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
        if (ShizukuCore.isReady()) {
            if (activeDpiPackage != null) {
                try { ShizukuCore.execute("leo density-reset " + activeDpiPackage); } catch (Exception ignored) {}
            }
            if (activeTouchPackage != null) {
                try { ShizukuCore.execute("leo touch-reset " + activeTouchPackage); } catch (Exception ignored) {}
            }
        }
        clearActiveDpi();
        clearActiveTouch();
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
