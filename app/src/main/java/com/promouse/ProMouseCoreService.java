package com.promouse;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProMouseCoreService extends Service {
    private static final String CHANNEL_ID = "promouse_core";
    private static final int NOTIFICATION_ID = 3199;
    private static final long SESSION_CHECK_MS = 30_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean checking = new AtomicBoolean(false);

    private final Runnable sessionCheck = new Runnable() {
        @Override
        public void run() {
            keepSessionWarm();
            handler.postDelayed(this, SESSION_CHECK_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, notification("ProMouse pronto em segundo plano"));
        handler.post(sessionCheck);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        keepSessionWarm();
        return START_STICKY;
    }

    private void keepSessionWarm() {
        if (!ActivationStore.isActive(this)) {
            updateNotification("ProMouse em segundo plano — aguardando ativação");
            return;
        }
        if (!"ADB Wi-Fi".equals(ActivationStore.method(this))) {
            updateNotification("ProMouse ativo em segundo plano — " + ActivationStore.method(this));
            return;
        }
        if (!checking.compareAndSet(false, true)) return;

        executor.execute(() -> {
            try {
                ProMouseAdbManager manager = ProMouseAdbManager.getInstance(this);
                boolean connected = manager.isConnected();
                if (!connected) {
                    try {
                        connected = manager.connectTls(this, 7000) || manager.isConnected();
                    } catch (Throwable ignored) {
                        connected = manager.isConnected();
                    }
                }
                final boolean ready = connected;
                handler.post(() -> updateNotification(ready
                        ? "ADB Wi-Fi conectado — sessão mantida em memória"
                        : "ADB Wi-Fi pareado — aguardando reconexão"));
            } catch (Throwable ignored) {
                handler.post(() -> updateNotification("ProMouse ativo — sessão ADB aguardando reconexão"));
            } finally {
                checking.set(false);
            }
        });
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID,
                    "ProMouse em segundo plano",
                    NotificationManager.IMPORTANCE_LOW));
        }
    }

    private Notification notification(String text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle("ProMouse")
                .setContentText(text)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, notification(text));
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopService(new Intent(this, OverlayService.class));
        stopService(new Intent(this, PairingDiscoveryService.class));
        handler.removeCallbacks(sessionCheck);
        try {
            ProMouseAdbManager manager = ProMouseAdbManager.getInstance(this);
            if (manager.isConnected()) manager.disconnect();
        } catch (Throwable ignored) {}
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(sessionCheck);
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
