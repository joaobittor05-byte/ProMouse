package com.promouse;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

public class PairingCodeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle results = RemoteInput.getResultsFromIntent(intent);
        if (results == null) {
            showStatus(context, "Código não recebido", "Abra o pareamento novamente e digite os 6 dígitos.");
            return;
        }

        CharSequence value = results.getCharSequence(ActivationActivity.REMOTE_INPUT_PAIR_CODE);
        String code = value == null ? "" : value.toString().trim();
        if (!code.matches("\\d{6}")) {
            ActivationStore.setAdbWifiState(context, "Código inválido");
            showStatus(context, "Código inválido", "O código de pareamento deve ter exatamente 6 dígitos.");
            return;
        }

        ActivationStore.setAdbPairingCode(context, code);
        ActivationStore.setAdbWifiState(context, "Código recebido — aguardando conexão");

        // Nesta etapa o código é capturado pelo ProMouse. A sessão NÃO é marcada como ativa
        // até que o futuro backend ADB confirme de verdade o handshake e o canal de transporte.
        showStatus(context,
                "Código recebido",
                "ProMouse recebeu os 6 dígitos. Aguardando confirmação do backend ADB Wi-Fi.");
    }

    private void showStatus(Context context, String title, String body) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, ActivationActivity.CHANNEL_ID)
                : new Notification.Builder(context);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("ProMouse — " + title)
                .setContentText(body)
                .setOngoing(false)
                .setAutoCancel(true)
                .build();
        nm.notify(ActivationActivity.PAIR_NOTIFICATION_ID, notification);
    }
}
