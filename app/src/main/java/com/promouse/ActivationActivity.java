package com.promouse;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ActivationActivity extends Activity {
    public static final String CHANNEL_ID = "promouse_activation";
    public static final int PAIR_NOTIFICATION_ID = 3101;
    public static final String REMOTE_INPUT_PAIR_CODE = "promouse_pair_code";

    private TextView state;
    private final int bg = Color.rgb(8, 12, 19);
    private final int card = Color.rgb(18, 25, 36);
    private final int border = Color.rgb(42, 54, 72);
    private final int muted = Color.rgb(145, 157, 177);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        requestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateState();
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(20));
        root.setBackgroundColor(bg);
        root.addView(text("Ativação", 27, Color.WHITE, true));
        root.addView(text("Escolha como o ProMouse será ativado.", 13, muted, false), top(5));

        state = text("", 14, Color.WHITE, true);
        LinearLayout stateCard = new LinearLayout(this);
        stateCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        stateCard.setBackground(round(card, 14, border));
        stateCard.addView(state);
        root.addView(stateCard, top(18));

        Button wifi = wide("ADB Wi-Fi");
        Button rootBtn = wide("ROOT");
        Button shell = wide("BShell");
        Button off = wide("Desativar ProMouse");
        root.addView(wifi, top(18));
        root.addView(rootBtn, top(10));
        root.addView(shell, top(10));
        root.addView(off, top(24));

        wifi.setOnClickListener(v -> startAdbWifiFlow());
        rootBtn.setOnClickListener(v -> requestRoot());
        shell.setOnClickListener(v -> showBShellCode());
        off.setOnClickListener(v -> {
            stopService(new Intent(this, PairingDiscoveryService.class));
            ActivationStore.deactivate(this);
            getSystemService(NotificationManager.class).cancel(PAIR_NOTIFICATION_ID);
            updateState();
            Toast.makeText(this, "ProMouse desativado", Toast.LENGTH_SHORT).show();
        });
        return root;
    }

    private void updateState() {
        if (state == null) return;
        boolean active = ActivationStore.isActive(this);
        String method = ActivationStore.method(this);
        String extra = "";
        if ("ADB Wi-Fi".equals(method) && !active) {
            extra = "\nPareamento: " + ActivationStore.adbWifiState(this);
            int port = ActivationStore.adbPairingPort(this);
            if (port > 0) extra += "\nPorta: " + port + " (automática)";
        }
        state.setText((active ? "● ATIVO" : "● DESATIVADO") + "\nMétodo: " + method + extra);
        state.setTextColor(active ? Color.rgb(102, 230, 149) : Color.rgb(235, 112, 112));
    }

    private void startAdbWifiFlow() {
        ActivationStore.beginAdbWifi(this);
        Intent discovery = new Intent(this, PairingDiscoveryService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(discovery);
        else startService(discovery);
        updateState();

        Toast.makeText(this,
                "Abra Depuração sem fio → Parear dispositivo com código. A porta será detectada automaticamente.",
                Toast.LENGTH_LONG).show();
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void requestRoot() {
        stopService(new Intent(this, PairingDiscoveryService.class));
        state.setText("● VERIFICANDO ROOT...");
        new Thread(() -> {
            boolean ok = false;
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line = br.readLine();
                int exit = p.waitFor();
                ok = exit == 0 && line != null && line.contains("uid=0");
            } catch (Exception ignored) {}
            boolean finalOk = ok;
            runOnUiThread(() -> {
                if (finalOk) {
                    ActivationStore.activate(this, "ROOT");
                    Toast.makeText(this, "Root concedido.", Toast.LENGTH_SHORT).show();
                } else {
                    ActivationStore.deactivate(this);
                    Toast.makeText(this, "Não foi possível obter acesso root.", Toast.LENGTH_LONG).show();
                }
                updateState();
            });
        }).start();
    }

    private void showBShellCode() {
        String code = ActivationStore.bshellCode(this);
        new android.app.AlertDialog.Builder(this)
                .setTitle("BShell")
                .setMessage("Código atual: " + code + "\n\nAbra Observações para copiar o comando de ativação pelo PC ou Brevent.")
                .setNegativeButton("Fechar", null)
                .setPositiveButton("Novo código", (d, w) -> {
                    String next = ActivationStore.regenerateBShellCode(this);
                    Toast.makeText(this, "Novo código: " + next, Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 91);
        }
    }

    private Button wide(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(18), 0, dp(18), 0);
        b.setBackground(round(card, 14, border));
        return b;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private LinearLayout.LayoutParams top(int value) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        lp.topMargin = dp(value);
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
