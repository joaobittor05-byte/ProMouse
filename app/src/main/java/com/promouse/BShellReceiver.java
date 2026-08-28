package com.promouse;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BShellReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"com.promouse.BSHELL_ACTIVATE".equals(intent.getAction())) return;
        String received = intent.getStringExtra("code");
        String expected = ActivationStore.bshellCode(context);
        if (expected.equals(received)) {
            ActivationStore.activate(context, "BShell");
        }
    }
}
