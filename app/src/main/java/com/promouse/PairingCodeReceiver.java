package com.promouse;

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
            ActivationStore.setAdbWifiState(context, "Código não recebido");
            refresh(context);
            return;
        }

        CharSequence value = results.getCharSequence(ActivationActivity.REMOTE_INPUT_PAIR_CODE);
        String code = value == null ? "" : value.toString().trim();
        if (!code.matches("\\d{6}")) {
            ActivationStore.setAdbWifiState(context, "Código inválido — use exatamente 6 dígitos");
            refresh(context);
            return;
        }

        ActivationStore.setAdbPairingCode(context, code);
        AdbWifiPairingEngine.tryStart(context);
        refresh(context);
    }

    private void refresh(Context context) {
        Intent refresh = new Intent(context, PairingDiscoveryService.class)
                .setAction(PairingDiscoveryService.ACTION_REFRESH);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(refresh);
        else context.startService(refresh);
    }
}
