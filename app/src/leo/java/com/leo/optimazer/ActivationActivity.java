package com.leo.optimazer;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class ActivationActivity extends Activity {
    private static final int BG = Color.rgb(10, 13, 18);
    private static final int CARD = Color.rgb(18, 24, 33);
    private static final int CARD_2 = Color.rgb(24, 32, 43);
    private static final int TEXT = Color.rgb(240, 246, 252);
    private static final int MUTED = Color.rgb(155, 168, 184);
    private static final int GOOD = Color.rgb(96, 211, 148);
    private static final int BAD = Color.rgb(255, 112, 112);

    private TextView secureStatus;
    private TextView usageStatus;
    private Button continueButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        refreshStatus(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus(false);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(40));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("LEO OPTIMAZER", 28, TEXT, true);
        root.addView(title);

        TextView subtitle = text("Ativação pelo Brevent", 15, MUTED, false);
        subtitle.setPadding(0, dp(3), 0, dp(22));
        root.addView(subtitle);

        LinearLayout card = card();
        secureStatus = text("● Permissão avançada: verificando…", 16, TEXT, true);
        usageStatus = text("● Acesso ao uso: verificando…", 16, TEXT, true);
        usageStatus.setPadding(0, dp(10), 0, 0);
        card.addView(secureStatus);
        card.addView(usageStatus);

        TextView explanation = text(
                "1. Copie o código abaixo e execute no terminal/comandos do Brevent.\n\n" +
                "2. Depois libere o Acesso ao uso para o Leo Optimazer. Isso permite detectar qual aplicativo está aberto e aplicar o perfil certo.\n\n" +
                "Não é necessário conectar o celular ao PC para esse modo.",
                13, MUTED, false
        );
        explanation.setPadding(0, dp(18), 0, dp(14));
        card.addView(explanation);

        card.addView(button("COPIAR CÓDIGO DO BREVENT", v -> copyBreventCommand()));
        card.addView(spacer(9));
        card.addView(button("ABRIR ACESSO AO USO", v -> openUsageAccess()));
        card.addView(spacer(9));
        card.addView(button("VERIFICAR ATIVAÇÃO", v -> refreshStatus(true)));
        root.addView(card);

        continueButton = button("ENTRAR NO LEO OPTIMAZER", v -> openMain());
        LinearLayout.LayoutParams continueLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        continueLp.topMargin = dp(18);
        root.addView(continueButton, continueLp);

        TextView commandPreview = text(
                "Código Brevent:\npm grant com.leo.optimazer android.permission.WRITE_SECURE_SETTINGS",
                12, MUTED, false
        );
        commandPreview.setPadding(0, dp(18), 0, 0);
        root.addView(commandPreview);

        setContentView(scroll);
    }

    private void refreshStatus(boolean showMessage) {
        PrivilegedOps ops = new PrivilegedOps(this);
        boolean secure = ops.hasWriteSecureSettings();
        boolean usage = ops.hasUsageAccess();

        secureStatus.setText(secure
                ? "● BREVENT: PERMISSÃO CONCEDIDA"
                : "● BREVENT: PERMISSÃO NÃO CONCEDIDA");
        secureStatus.setTextColor(secure ? GOOD : BAD);

        usageStatus.setText(usage
                ? "● ACESSO AO USO: LIBERADO"
                : "● ACESSO AO USO: PENDENTE");
        usageStatus.setTextColor(usage ? GOOD : BAD);

        continueButton.setEnabled(secure && usage);
        continueButton.setAlpha((secure && usage) ? 1f : 0.45f);

        if (showMessage) {
            if (secure && usage) {
                Toast.makeText(this, "Ativação concluída", Toast.LENGTH_SHORT).show();
            } else if (!secure) {
                Toast.makeText(this, "Execute primeiro o código no Brevent", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Falta liberar o Acesso ao uso", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void copyBreventCommand() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(
                "Ativação Leo Optimazer via Brevent",
                PrivilegedOps.breventActivationCommand()
        ));
        Toast.makeText(this, "Código do Brevent copiado", Toast.LENGTH_SHORT).show();
    }

    private void openUsageAccess() {
        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }
    }

    private void openMain() {
        PrivilegedOps ops = new PrivilegedOps(this);
        if (!ops.isActivated()) {
            refreshStatus(true);
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
