package com.leo.optimazer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class FrameRepeatActivity extends Activity {
    private static final int BG = Color.rgb(7, 10, 17);
    private static final int CARD = Color.rgb(16, 22, 34);
    private static final int TEXT = Color.rgb(244, 248, 255);
    private static final int MUTED = Color.rgb(150, 164, 188);
    private static final int CYAN = Color.rgb(61, 214, 255);

    @Override protected void onCreate(Bundle savedInstanceState) { super.onCreate(savedInstanceState); render(); }
    @Override protected void onResume() { super.onResume(); render(); }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        root.addView(text("FRAME REPEAT", 28, TEXT, true));
        TextView sub = text("A → A → B → B • sem IA • sem previsão", 14, CYAN, true);
        sub.setPadding(0, dp(2), 0, dp(8)); root.addView(sub);
        root.addView(text("Competitivo prioriza menor latência. Qualidade prioriza múltiplos perfeitos de FPS/Hz. Suave busca cadência estável com repetição moderada.", 12, MUTED, false));

        List<ProfileStore.Profile> profiles = ProfileStore.all(this);
        if (profiles.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("Nenhum perfil de aplicativo criado ainda.", 15, TEXT, true));
            TextView hint = text("Abra Otimização e Perfis, adicione o jogo e depois volte aqui.", 12, MUTED, false);
            hint.setPadding(0, dp(7), 0, 0); empty.addView(hint);
            root.addView(space(16)); root.addView(empty);
            setContentView(scroll); return;
        }

        String[] labels = {"Competitivo", "Qualidade", "Suave"};
        for (ProfileStore.Profile profile : profiles) {
            root.addView(space(14));
            LinearLayout box = card();
            box.addView(text(profile.packageName, 15, TEXT, true));

            boolean enabled = FrameRepeatPrefs.isEnabled(this, profile.packageName, true);
            CheckBox toggle = new CheckBox(this);
            toggle.setText("Ativar Frame Repeat");
            toggle.setTextColor(TEXT);
            toggle.setChecked(enabled);
            toggle.setPadding(0, dp(8), 0, dp(6));
            box.addView(toggle);

            TextView modeLabel = text("Modo", 12, MUTED, true);
            modeLabel.setPadding(0, dp(4), 0, dp(4)); box.addView(modeLabel);
            Spinner spinner = new Spinner(this);
            spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
            String current = FrameRepeatPrefs.mode(this, profile.packageName);
            spinner.setSelection(FrameRepeatPrefs.MODE_QUALITY.equals(current) ? 1 : FrameRepeatPrefs.MODE_SMOOTH.equals(current) ? 2 : 0);
            box.addView(spinner, new LinearLayout.LayoutParams(-1, dp(52)));

            TextView modeHelp = text(modeDescription(current), 12, MUTED, false);
            modeHelp.setPadding(0, dp(5), 0, 0); box.addView(modeHelp);

            toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                FrameRepeatPrefs.setEnabled(this, profile.packageName, isChecked);
                ensureFrameService();
                Toast.makeText(this, isChecked ? "Frame Repeat ativado" : "Frame Repeat desativado", Toast.LENGTH_SHORT).show();
            });
            spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                boolean first = true;
                @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    String mode = position == 1 ? FrameRepeatPrefs.MODE_QUALITY : position == 2 ? FrameRepeatPrefs.MODE_SMOOTH : FrameRepeatPrefs.MODE_COMPETITIVE;
                    FrameRepeatPrefs.setMode(FrameRepeatActivity.this, profile.packageName, mode);
                    modeHelp.setText(modeDescription(mode));
                    ensureFrameService();
                    if (!first) Toast.makeText(FrameRepeatActivity.this, "Modo: " + FrameRepeatPrefs.friendlyName(mode), Toast.LENGTH_SHORT).show();
                    first = false;
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
            root.addView(box);
        }

        TextView footer = text("Compatibilidade por capacidade: Snapdragon, MediaTek, Exynos, Tensor, Unisoc e outros SoCs Android.", 11, MUTED, false);
        footer.setGravity(Gravity.CENTER); footer.setPadding(0, dp(20), 0, 0); root.addView(footer);
        setContentView(scroll);
        ensureFrameService();
    }

    private String modeDescription(String mode) {
        String n = FrameRepeatPrefs.normalizeMode(mode);
        if (FrameRepeatPrefs.MODE_QUALITY.equals(n)) return "Qualidade: procura múltiplo inteiro de FPS/Hz para cadência mais limpa.";
        if (FrameRepeatPrefs.MODE_SMOOTH.equals(n)) return "Suave: evita repetição excessiva e busca estabilidade visual.";
        return "Competitivo: usa o maior refresh útil e prioriza o frame mais recente para reduzir latência percebida.";
    }

    private void ensureFrameService() {
        try {
            Intent intent = new Intent(this, FrameRepeatService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
        } catch (Throwable ignored) {}
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(CARD); bg.setCornerRadius(dp(18)); bg.setStroke(dp(1), Color.rgb(35, 49, 72)); l.setBackground(bg); return l;
    }
    private TextView text(String s, int sp, int color, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); if (bold) v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD); return v; }
    private View space(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
