package com.leo.optimazer;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

public class ActivationActivity extends Activity {
    private static final int BG = Color.rgb(10, 13, 18);
    private static final int CARD = Color.rgb(18, 24, 33);
    private static final int CARD_2 = Color.rgb(24, 32, 43);
    private static final int TEXT = Color.rgb(240, 246, 252);
    private static final int MUTED = Color.rgb(155, 168, 184);
    private static final int GOOD = Color.rgb(96, 211, 148);
    private static final int BAD = Color.rgb(255, 112, 112);
    private static final int WARN = Color.rgb(255, 193, 92);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView managerStatus;
    private TextView serverStatus;
    private TextView permissionStatus;
    private TextView coreStatus;
    private TextView diagnosticStatus;
    private Button continueButton;

    private final Runnable refreshLoop = new Runnable() {
        @Override public void run() {
            refreshStatus();
            if (!isFinishing()) handler.postDelayed(this, 700L);
        }
    };

    private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grantResult) -> {
        if (requestCode != ShizukuCore.REQUEST_CODE) return;
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            ShizukuCore.retryBind();
            Toast.makeText(this, "Permissão do Shizuku concedida", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Permissão do Shizuku negada", Toast.LENGTH_LONG).show();
        }
        refreshStatus();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        buildUi();
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ShizukuCore.hasPermission()) ShizukuCore.bindUserService();
        handler.removeCallbacks(refreshLoop);
        handler.post(refreshLoop);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshLoop);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refreshLoop);
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(40));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(text("LEO OPTIMAZER", 28, TEXT, true));
        TextView subtitle = text("Núcleo principal • Shizuku", 15, MUTED, false);
        subtitle.setPadding(0, dp(3), 0, dp(22));
        root.addView(subtitle);

        LinearLayout card = card();
        managerStatus = text("● Shizuku: verificando…", 15, TEXT, true);
        serverStatus = text("● Serviço: verificando…", 15, TEXT, true);
        permissionStatus = text("● Permissão: verificando…", 15, TEXT, true);
        coreStatus = text("● Núcleo Leo: verificando…", 15, TEXT, true);
        diagnosticStatus = text("Diagnóstico: aguardando…", 12, MUTED, false);
        serverStatus.setPadding(0, dp(9), 0, 0);
        permissionStatus.setPadding(0, dp(9), 0, 0);
        coreStatus.setPadding(0, dp(9), 0, 0);
        diagnosticStatus.setPadding(0, dp(9), 0, 0);
        card.addView(managerStatus);
        card.addView(serverStatus);
        card.addView(permissionStatus);
        card.addView(coreStatus);
        card.addView(diagnosticStatus);

        TextView explanation = text(
                "O Shizuku entrega um Binder ao Leo por meio do ShizukuProvider. Com a autorização concedida, o Leo inicia um UserService privilegiado próprio para RAM e perfis individuais.\n\n" +
                "Sem root, o Shizuku precisa ser iniciado novamente depois que o celular reiniciar.",
                13, MUTED, false
        );
        explanation.setPadding(0, dp(18), 0, dp(14));
        card.addView(explanation);

        card.addView(button("ABRIR SHIZUKU", v -> openShizuku()));
        card.addView(spacer(9));
        card.addView(button("SOLICITAR PERMISSÃO AO SHIZUKU", v -> requestShizukuPermission()));
        card.addView(spacer(9));
        card.addView(button("CONECTAR / TENTAR NOVAMENTE", v -> connectCore()));
        card.addView(spacer(9));
        card.addView(button("VERIFICAR ESTADO", v -> refreshStatus()));
        root.addView(card);

        continueButton = button("ENTRAR NO LEO OPTIMAZER", v -> openMain());
        LinearLayout.LayoutParams continueLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        continueLp.topMargin = dp(18);
        root.addView(continueButton, continueLp);

        TextView note = text(
                "O botão Entrar só é liberado quando o servidor Shizuku, a permissão e o UserService do Leo estiverem conectados.",
                12, MUTED, false
        );
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note);

        setContentView(scroll);
    }

    private void refreshStatus() {
        boolean installed = ShizukuCore.isManagerInstalled();
        boolean alive = ShizukuCore.isBinderAlive();
        boolean permission = ShizukuCore.hasPermission();
        boolean ready = ShizukuCore.isReady();

        managerStatus.setText(installed ? "● SHIZUKU INSTALADO" : "● SHIZUKU NÃO INSTALADO");
        managerStatus.setTextColor(installed ? GOOD : BAD);

        serverStatus.setText(alive ? "● SERVIÇO SHIZUKU EM EXECUÇÃO" : "● SERVIÇO SHIZUKU PARADO");
        serverStatus.setTextColor(alive ? GOOD : WARN);

        permissionStatus.setText(permission ? "● LEO AUTORIZADO NO SHIZUKU" : "● PERMISSÃO DO LEO PENDENTE");
        permissionStatus.setTextColor(permission ? GOOD : BAD);

        if (ready) {
            int uid = ShizukuCore.getServiceUid();
            coreStatus.setText(uid == 0 ? "● NÚCLEO LEO ATIVO • ROOT" : "● NÚCLEO LEO ATIVO • SHELL");
            coreStatus.setTextColor(GOOD);
            diagnosticStatus.setText("Diagnóstico: conexão Binder + UserService OK");
            diagnosticStatus.setTextColor(GOOD);
        } else if (permission && alive) {
            boolean connecting = ShizukuCore.isBinding();
            String error = ShizukuCore.getLastBindError();
            coreStatus.setText(connecting ? "● NÚCLEO LEO CONECTANDO…" : "● NÚCLEO LEO NÃO CONECTADO");
            coreStatus.setTextColor(connecting ? WARN : BAD);
            diagnosticStatus.setText(error.isEmpty() ? "Diagnóstico: aguardando UserService" : "Diagnóstico: " + error);
            diagnosticStatus.setTextColor(error.isEmpty() ? WARN : BAD);
        } else {
            coreStatus.setText("● NÚCLEO LEO AGUARDANDO SHIZUKU");
            coreStatus.setTextColor(BAD);
            String error = ShizukuCore.getLastBindError();
            diagnosticStatus.setText(error.isEmpty() ? "Diagnóstico: —" : "Diagnóstico: " + error);
            diagnosticStatus.setTextColor(error.isEmpty() ? MUTED : BAD);
        }

        continueButton.setEnabled(ready);
        continueButton.setAlpha(ready ? 1f : 0.45f);
    }

    private void openShizuku() {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if (launch == null) throw new IllegalStateException();
            startActivity(launch);
        } catch (Exception e) {
            Toast.makeText(this, "Instale o Shizuku primeiro", Toast.LENGTH_LONG).show();
        }
    }

    private void requestShizukuPermission() {
        try {
            ShizukuCore.requestPermission();
            if (ShizukuCore.hasPermission()) {
                ShizukuCore.retryBind();
                Toast.makeText(this, "Permissão já concedida • conectando núcleo", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage() == null ? "Shizuku não está ativo" : e.getMessage(), Toast.LENGTH_LONG).show();
        }
        refreshStatus();
    }

    private void connectCore() {
        if (!ShizukuCore.isBinderAlive()) {
            Toast.makeText(this, "O servidor do Shizuku não foi encontrado", Toast.LENGTH_LONG).show();
            refreshStatus();
            return;
        }
        if (!ShizukuCore.hasPermission()) {
            requestShizukuPermission();
            return;
        }
        ShizukuCore.retryBind();
        Toast.makeText(this, "Nova tentativa de conexão iniciada", Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private void openMain() {
        if (!ShizukuCore.isReady()) {
            Toast.makeText(this, "Conecte o núcleo Shizuku primeiro", Toast.LENGTH_LONG).show();
            refreshStatus();
            return;
        }
        startActivity(new Intent(this, MainActivity.class));
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(17), dp(17), dp(17), dp(17));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), CARD_2);
        layout.setBackground(bg);
        return layout;
    }

    private Button button(String label, android.view.View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(TEXT);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD_2);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.rgb(47, 69, 91));
        b.setBackground(bg);
        b.setOnClickListener(listener);
        b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        return b;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        return tv;
    }

    private android.view.View spacer(int heightDp) {
        android.view.View v = new android.view.View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return v;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
