package com.promouse;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.app.Service;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.IBinder;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public class PairingDiscoveryService extends Service {
    public static final String ACTION_REFRESH = "com.promouse.action.PAIRING_REFRESH";
    private static final String SERVICE_TYPE = "_adb-tls-pairing._tcp.";

    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private boolean discoveryRunning;
    private boolean resolving;
    private String resolvedServiceName;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(ActivationActivity.PAIR_NOTIFICATION_ID, buildNotification());
        nsdManager = (NsdManager) getSystemService(NSD_SERVICE);
        startDiscovery();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        updateNotification();
        if (!discoveryRunning) startDiscovery();
        AdbWifiPairingEngine.tryStart(this);
        return START_NOT_STICKY;
    }

    private void startDiscovery() {
        if (nsdManager == null || discoveryRunning) return;
        if (!ActivationStore.isActive(this)) {
            ActivationStore.setAdbWifiState(this,
                    ActivationStore.adbPairingCode(this).matches("\\d{6}")
                            ? "Código recebido — procurando porta automaticamente"
                            : "Procurando porta automaticamente");
        }

        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String serviceType) {
                discoveryRunning = true;
                updateNotification();
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                if (serviceInfo == null || resolving) return;
                String type = serviceInfo.getServiceType();
                if (type == null || !type.contains("_adb-tls-pairing._tcp")) return;
                resolve(serviceInfo);
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                if (serviceInfo == null || resolvedServiceName == null) return;
                if (resolvedServiceName.equals(serviceInfo.getServiceName()) && !ActivationStore.isActive(PairingDiscoveryService.this)) {
                    resolvedServiceName = null;
                    ActivationStore.clearAdbPairingEndpoint(PairingDiscoveryService.this);
                    ActivationStore.setAdbWifiState(PairingDiscoveryService.this,
                            "Janela de pareamento fechada — abra novamente");
                    updateNotification();
                }
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                discoveryRunning = false;
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                discoveryRunning = false;
                ActivationStore.setAdbWifiState(PairingDiscoveryService.this,
                        "Não foi possível procurar a porta (NSD " + errorCode + ")");
                updateNotification();
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                discoveryRunning = false;
            }
        };

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        } catch (Exception e) {
            discoveryRunning = false;
            ActivationStore.setAdbWifiState(this, "Falha ao iniciar descoberta da porta");
            updateNotification();
        }
    }

    @SuppressWarnings("deprecation")
    private void resolve(NsdServiceInfo serviceInfo) {
        resolving = true;
        try {
            nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                @Override
                public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    resolving = false;
                }

                @Override
                public void onServiceResolved(NsdServiceInfo resolved) {
                    resolving = false;
                    if (resolved == null || resolved.getHost() == null || resolved.getPort() <= 0) return;
                    InetAddress host = resolved.getHost();
                    if (!isAddressOnThisDevice(host)) return;

                    resolvedServiceName = resolved.getServiceName();
                    ActivationStore.setAdbPairingEndpoint(
                            PairingDiscoveryService.this,
                            host.getHostAddress(),
                            resolved.getPort());
                    updateNotification();
                    AdbWifiPairingEngine.tryStart(PairingDiscoveryService.this);
                }
            });
        } catch (Exception e) {
            resolving = false;
        }
    }

    private boolean isAddressOnThisDevice(InetAddress candidate) {
        if (candidate == null) return false;
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp()) continue;
                for (InetAddress local : Collections.list(iface.getInetAddresses())) {
                    if (candidate.equals(local)) return true;
                }
            }
        } catch (Exception ignored) {}
        return candidate.isLoopbackAddress();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(
                    ActivationActivity.CHANNEL_ID,
                    "Ativação ProMouse",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Descoberta automática da porta e pareamento ADB Wi-Fi");
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, ActivationActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(
                this, 33, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent replyIntent = new Intent(this, PairingCodeReceiver.class);
        int replyFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) replyFlags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent replyPi = PendingIntent.getBroadcast(this, 34, replyIntent, replyFlags);

        RemoteInput remoteInput = new RemoteInput.Builder(ActivationActivity.REMOTE_INPUT_PAIR_CODE)
                .setLabel("Código de 6 dígitos")
                .build();
        Notification.Action action = new Notification.Action.Builder(
                android.R.drawable.ic_menu_send,
                "DIGITAR CÓDIGO",
                replyPi)
                .addRemoteInput(remoteInput)
                .build();

        int port = ActivationStore.adbPairingPort(this);
        String code = ActivationStore.adbPairingCode(this);
        String state = ActivationStore.adbWifiState(this);
        String body;
        if (state.startsWith("Pareando") || state.startsWith("Pareado") || state.startsWith("Falha")) {
            body = state;
        } else if (port <= 0) {
            body = "Abra 'Parear dispositivo com código'. Procurando a porta automaticamente...";
        } else if (!code.matches("\\d{6}")) {
            body = "Porta " + port + " detectada automaticamente. Digite apenas o código.";
        } else {
            body = "Porta " + port + " e código recebidos. Iniciando pareamento ADB...";
        }

        return new Notification.Builder(this, ActivationActivity.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("ProMouse — ADB Wi-Fi")
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(openPi)
                .setOngoing(true)
                .addAction(action)
                .build();
    }

    private void updateNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(ActivationActivity.PAIR_NOTIFICATION_ID, buildNotification());
    }

    private void stopDiscovery() {
        if (nsdManager == null || discoveryListener == null || !discoveryRunning) return;
        try {
            nsdManager.stopServiceDiscovery(discoveryListener);
        } catch (Exception ignored) {}
        discoveryRunning = false;
    }

    @Override
    public void onDestroy() {
        stopDiscovery();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
