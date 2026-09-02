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
    private long nextCleanupAt = Long.MAX_VALUE;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, notification("Temporizador ativo"));
        workerThread = new HandlerThread("LeoOptimazer-Timer");
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

            if (sec < 10L) {
                nextCleanupAt = Long.MAX_VALUE;
                updateNotification("Temporizador de RAM desligado");
            } else if (now >= nextCleanupAt) {
                updateNotification("Intervalo atingido • toque para executar a limpeza pelo Brevent");
                nextCleanupAt = now + sec * 1000L;
            } else {
                long remaining = Math.max(0L, (nextCleanupAt - now + 999L) / 1000L);
                updateNotification("Próxima limpeza em " + remaining + "s");
            }

            if (worker != null) worker.postDelayed(this, 1000L);
        }
    };

    private synchronized void scheduleFromPreferences() {
        long sec = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_INTERVAL_SEC, 0L);
        nextCleanupAt = sec < 10L ? Long.MAX_VALUE : SystemClock.elapsedRealtime() + sec * 1000L;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Leo Optimazer", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Temporizador e acesso rápido à limpeza Brevent");
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
                .setContentTitle("Leo Optimazer")
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
