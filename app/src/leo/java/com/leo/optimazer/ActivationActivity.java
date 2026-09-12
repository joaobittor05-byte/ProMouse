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
    private Button continueButton,connectButton;

    private final Runnable refreshLoop = new Runnable() {
        @Override public void run() {
            refreshStatus();
            if (!isFinishing()) handler.postDelayed(this, 700L);
        }
    };

    private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grantResult) -> {
        if (requestCode != ShizukuCore.REQUEST_CODE) return;
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permissão do Shizuku concedida", Toast.LENGTH_SHORT).show();
            // Dá tempo para o Binder/provider estabilizar antes de tentar UserService.
            handler.postDelayed(() -> {
                if (!ShizukuCore.isReady()) ShizukuCore.retryBind();
                refreshStatus();
            }, 1000L);
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
        handler.removeCallbacks(refreshLoop);
        handler.post(refreshLoop);

        if (ShizukuCore.hasPermission()) {
            // Não faz bind durante Application.onCreate. Espera a Activity estar estável.
            handler.postDelayed(() -> {
                if (!isFinishing() && !ShizukuCore.isReady()) {
                    ShizukuCore.bindUserService();
                    refreshStatus();
                }
            }, 1400L);
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshLoop);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        super.onDestroy();
    }

    private void buildUi(){
        LinearLayout root=LeoUi.page(this,"Conexão","Prepare o Leo para jogar.",-1,true),panel=LeoUi.card(this);
        panel.addView(new LeoUi.Glyph(this,"shield",LeoUi.CYAN),new LinearLayout.LayoutParams(dp(56),dp(56)));
        coreStatus=LeoUi.text(this,"Verificando…",23,LeoUi.TEXT,true);panel.addView(coreStatus);panel.addView(LeoUi.gap(this,18));
        managerStatus=LeoUi.text(this,"",14,LeoUi.MUTED,false);serverStatus=LeoUi.text(this,"",14,LeoUi.MUTED,false);permissionStatus=LeoUi.text(this,"",14,LeoUi.MUTED,false);
        panel.addView(managerStatus);panel.addView(LeoUi.gap(this,12));panel.addView(serverStatus);panel.addView(LeoUi.gap(this,12));panel.addView(permissionStatus);root.addView(panel);root.addView(LeoUi.gap(this,20));
        connectButton=LeoUi.button(this,"Conectar",true,v->{if(!ShizukuCore.isBinderAlive())openShizuku();else if(!ShizukuCore.hasPermission())requestShizukuPermission();else connectCore();});root.addView(connectButton);root.addView(LeoUi.gap(this,10));
        continueButton=LeoUi.button(this,"Continuar",false,v->openMain());root.addView(continueButton);root.addView(LeoUi.gap(this,18));root.addView(LeoUi.text(this,"Sem root, inicie o Shizuku novamente após reiniciar o celular.",13,LeoUi.MUTED,false));root.addView(LeoUi.gap(this,24));
        root.addView(LeoUi.link(this,"settings","Diagnóstico","Detalhes da conexão",v->startActivity(new Intent(this,DiagnosticsActivity.class))));
    }
    private void refreshStatus(){
        boolean installed=ShizukuCore.isManagerInstalled(),alive=ShizukuCore.isBinderAlive(),permission=ShizukuCore.hasPermission(),ready=ShizukuCore.isOperational();
        managerStatus.setText(installed?"✓  Shizuku instalado":"○  Instale o Shizuku");serverStatus.setText(alive?"✓  Serviço iniciado":"○  Inicie o serviço");permissionStatus.setText(permission?"✓  Acesso autorizado":"○  Autorize o acesso");
        coreStatus.setText(ready?"Conectado":"Conexão pendente");coreStatus.setTextColor(ready?LeoUi.GREEN:LeoUi.WARN);connectButton.setText(!alive?"Abrir Shizuku":!permission?"Autorizar acesso":"Reconectar");continueButton.setEnabled(ready);continueButton.setAlpha(ready?1f:.45f);
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
                Toast.makeText(this, "Permissão concedida", Toast.LENGTH_SHORT).show();
                handler.postDelayed(() -> {
                    ShizukuCore.retryBind();
                    refreshStatus();
                }, 900L);
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
        Toast.makeText(this,
                ShizukuCore.isFallbackReady()
                        ? "Conexão disponível. Atualizando…"
                        : "Nova tentativa de conexão iniciada",
                Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private void openMain() {
        if (!ShizukuCore.isOperational()) {
            Toast.makeText(this, "Autorize e inicie o Shizuku primeiro", Toast.LENGTH_LONG).show();
            refreshStatus();
            return;
        }
        LeoUi.navigate(this,0);
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
