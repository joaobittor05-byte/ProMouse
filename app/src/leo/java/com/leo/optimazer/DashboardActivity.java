package com.leo.optimazer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class DashboardActivity extends Activity {
    private static final int BG = Color.rgb(7, 10, 17);
    private static final int CARD = Color.rgb(16, 22, 34);
    private static final int TEXT = Color.rgb(244, 248, 255);
    private static final int MUTED = Color.rgb(150, 164, 188);
    private static final int CYAN = Color.rgb(61, 214, 255);
    private static final int PURPLE = Color.rgb(122, 92, 255);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(dp(20), dp(24), dp(20), dp(22));
        GradientDrawable heroBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(23, 39, 72), Color.rgb(42, 27, 78), Color.rgb(13, 20, 33)});
        heroBg.setCornerRadius(dp(24));
        heroBg.setStroke(dp(1), Color.rgb(64, 92, 142));
        hero.setBackground(heroBg);

        TextView logo = text("L", 34, TEXT, true);
        logo.setGravity(Gravity.CENTER);
        GradientDrawable logoBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{CYAN, PURPLE});
        logoBg.setShape(GradientDrawable.OVAL);
        logo.setBackground(logoBg);
        hero.addView(logo, new LinearLayout.LayoutParams(dp(72), dp(72)));
        hero.addView(space(12));
        hero.addView(text("LEO OPTIMAZER", 28, TEXT, true));
        TextView sub = text("Universal Performance Suite", 14, CYAN, true);
        sub.setPadding(0, dp(3), 0, 0);
        hero.addView(sub);
        TextView desc = text("Frame Repeat • Touch Engine • DPI/Resolução • RAM", 12, MUTED, false);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, dp(10), 0, 0);
        hero.addView(desc);
        root.addView(hero);

        root.addView(title("DISPOSITIVO"));
        LinearLayout device = card();
        device.addView(text(DeviceCompat.summary(), 13, TEXT, false));
        root.addView(device);

        root.addView(title("FRAME REPEAT"));
        LinearLayout frame = card();
        frame.addView(text("A → A → B → B", 23, CYAN, true));
        TextView frameDesc = text("Fluidez por repetição do último buffer, sem IA e sem geração de quadros.", 13, MUTED, false);
        frameDesc.setPadding(0, dp(6), 0, dp(12));
        frame.addView(frameDesc);
        frame.addView(primaryButton("CONFIGURAR FRAME REPEAT", v -> startActivity(new Intent(this, FrameRepeatActivity.class))));
        root.addView(frame);

        root.addView(title("FERRAMENTAS"));
        LinearLayout tools = card();
        tools.addView(button("SHIZUKU / ATIVAÇÃO", v -> startActivity(new Intent(this, ActivationActivity.class))));
        tools.addView(space(8));
        tools.addView(button("OTIMIZAÇÃO E PERFIS", v -> startActivity(new Intent(this, MainActivity.class))));
        root.addView(tools);

        TextView footer = text("Alpha17 Universal • arquitetura por capacidade, não por fabricante", 11, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(20), 0, 0);
        root.addView(footer);
        setContentView(scroll);
    }

    private TextView title(String s) {
        TextView v = text(s, 12, CYAN, true);
        v.setPadding(0, dp(20), 0, dp(8));
        return v;
    }
    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD); bg.setCornerRadius(dp(18)); bg.setStroke(dp(1), Color.rgb(35, 49, 72));
        l.setBackground(bg); return l;
    }
    private Button primaryButton(String s, View.OnClickListener listener) {
        Button b = button(s, listener);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.rgb(34, 166, 219), Color.rgb(104, 72, 235)});
        bg.setCornerRadius(dp(14)); b.setBackground(bg); return b;
    }
    private Button button(String s, View.OnClickListener listener) {
        Button b = new Button(this); b.setText(s); b.setTextColor(TEXT); b.setTextSize(13); b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.rgb(24, 33, 49)); bg.setCornerRadius(dp(14)); bg.setStroke(dp(1), Color.rgb(53, 73, 102));
        b.setBackground(bg); b.setOnClickListener(listener); b.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(50))); return b;
    }
    private TextView text(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color);
        if (bold) v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD); return v;
    }
    private View space(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
