package com.promouse;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AdbWifiPairingEngine {
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private AdbWifiPairingEngine() {}

    public static void tryStart(Context context) {
        Context app = context.getApplicationContext();
        if (ActivationStore.isActive(app)) return;

        String host = ActivationStore.adbPairingHost(app);
        int port = ActivationStore.adbPairingPort(app);
        String code = ActivationStore.adbPairingCode(app);
        if (host == null || host.isEmpty() || port <= 0 || !code.matches("\\d{6}")) return;
        if (!RUNNING.compareAndSet(false, true)) return;

        ActivationStore.setAdbWifiState(app, "Pareando com ADB Wi-Fi...");
        refreshDiscovery(app);

        EXECUTOR.execute(() -> {
            try {
                ProMouseAdbManager manager = ProMouseAdbManager.getInstance(app);
                boolean paired = manager.pair(host, port, code);
                if (!paired) throw new IllegalStateException("Pareamento recusado");

                ActivationStore.setAdbWifiState(app, "Pareado — conectando ao ADB...");
                refreshDiscovery(app);

                boolean connected = manager.isConnected() || manager.connectTls(app, 15000);
                if (!connected && !manager.isConnected()) {
                    throw new IllegalStateException("Pareado, mas a conexão ADB não abriu");
                }

                ActivationStore.activate(app, "ADB Wi-Fi");
                ActivationStore.setAdbWifiState(app, "Conectado");
                app.stopService(new Intent(app, PairingDiscoveryService.class));
                showResult(app, true, "ADB Wi-Fi conectado", "Pareamento concluído. ProMouse está ativo.");
            } catch (Throwable e) {
                String detail = e.getMessage();
                if (detail == null || detail.trim().isEmpty()) detail = e.getClass().getSimpleName();
                ActivationStore.setAdbWifiState(app, "Falha no pareamento: " + detail);
                showResult(app, false, "Falha no pareamento", detail);
            } finally {
                RUNNING.set(false);
                if (!ActivationStore.isActive(app)) refreshDiscovery(app);
            }
        });
    }

    private static void refreshDiscovery(Context context) {
        Intent refresh = new Intent(context, PairingDiscoveryService.class)
                .setAction(PairingDiscoveryService.ACTION_REFRESH);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(refresh);
            else context.startService(refresh);
        } catch (Exception ignored) {}
    }

    private static void showResult(Context context, boolean ok, String title, String body) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, ActivationActivity.CHANNEL_ID)
                : new Notification.Builder(context);
        Notification notification = builder
                .setSmallIcon(ok ? android.R.drawable.stat_sys_upload_done : android.R.drawable.stat_notify_error)
                .setContentTitle("ProMouse — " + title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .build();
        nm.notify(ActivationActivity.PAIR_NOTIFICATION_ID, notification);
    }
}
