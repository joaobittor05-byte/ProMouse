package com.promouse;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView accessibilityStatus;
    private TextView overlayStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(9, 13, 20));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(24), dp(22), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = text("ProMouse", 31, Color.WHITE, true);
        root.addView(title);
        TextView subtitle = text("v1.0 Alpha • Keyboard/Mouse → Touch", 15, Color.rgb(130, 184, 235), false);
        root.addView(subtitle, marginTop(4));

        TextView intro = text("Primeira build revivida do mapeador. O serviço converte teclas e botões do mouse em gestos Android reais usando dispatchGesture().", 16, Color.rgb(205, 215, 229), false);
        root.addView(intro, marginTop(20));

        accessibilityStatus = text("Acessibilidade: verificando…", 16, Color.WHITE, true);
        root.addView(accessibilityStatus, marginTop(24));

        Button accessibility = button("1. ATIVAR SERVIÇO DE MAPEAMENTO");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility, marginTop(10));

        overlayStatus = text("Overlay: verificando…", 16, Color.WHITE, true);
        root.addView(overlayStatus, marginTop(24));

        Button overlay = button("2. ATIVAR BOLHA FLUTUANTE");
        overlay.setOnClickListener(v -> enableOverlay());
        root.addView(overlay, marginTop(10));

        Button stopOverlay = button("PARAR BOLHA");
        stopOverlay.setOnClickListener(v -> {
            stopService(new Intent(this, OverlayService.class));
            Toast.makeText(this, "Bolha encerrada", Toast.LENGTH_SHORT).show();
        });
        root.addView(stopOverlay, marginTop(10));

        Button test = button("TESTAR TOQUE NO CENTRO");
        test.setOnClickListener(v -> testCenterTap());
        root.addView(test, marginTop(18));

        TextView mapTitle = text("MAPA PADRÃO DA BUILD", 14, Color.rgb(130, 184, 235), true);
        root.addView(mapTitle, marginTop(28));
        TextView map = text("W/A/S/D = direção do analógico\nSPACE = pulo\nCTRL = agachar\nSHIFT = corrida\nMouse esquerdo = atirar\nMouse direito = mirar\n\nAs posições são proporcionais à tela. O próximo passo será o editor visual para arrastar cada comando exatamente para cima do HUD do jogo.", 15, Color.rgb(210, 218, 230), false);
        root.addView(map, marginTop(8));

        TextView note = text("Importante: o ProMouse não lê o conteúdo da tela. A acessibilidade é usada somente para receber teclas compatíveis e executar gestos.", 13, Color.rgb(145, 154, 170), false);
        root.addView(note, marginTop(24));

        return scroll;
    }

    private void enableOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }
        startService(new Intent(this, OverlayService.class));
        overlayStatus.setText("Overlay: permitido • bolha iniciada");
        Toast.makeText(this, "Bolha ProMouse iniciada", Toast.LENGTH_SHORT).show();
    }

    private void testCenterTap() {
        MapperAccessibilityService service = MapperAccessibilityService.getInstance();
        if (service == null) {
            Toast.makeText(this, "Ative o serviço ProMouse em Acessibilidade", Toast.LENGTH_LONG).show();
            return;
        }
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        boolean sent = service.tap(dm.widthPixels * 0.5f, dm.heightPixels * 0.5f);
        Toast.makeText(this, sent ? "Gesto enviado" : "Não foi possível enviar o gesto", Toast.LENGTH_SHORT).show();
    }

    private void refreshStatus() {
        boolean accessibilityOn = MapperAccessibilityService.getInstance() != null;
        accessibilityStatus.setText(accessibilityOn ?
                "Acessibilidade: ATIVA" : "Acessibilidade: desativada");
        overlayStatus.setText(Settings.canDrawOverlays(this) ?
                "Overlay: PERMITIDO" : "Overlay: sem permissão");
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setBackgroundColor(Color.rgb(25, 103, 190));
        b.setMinHeight(dp(50));
        return b;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams marginTop(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(topDp);
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
