package com.leo.optimazer;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(10, 13, 18);
    private static final int CARD = Color.rgb(18, 24, 33);
    private static final int CARD_2 = Color.rgb(24, 32, 43);
    private static final int TEXT = Color.rgb(240, 246, 252);
    private static final int MUTED = Color.rgb(155, 168, 184);
    private static final int ACCENT = Color.rgb(99, 179, 255);
    private static final int GOOD = Color.rgb(96, 211, 148);
    private static final int BAD = Color.rgb(255, 112, 112);

    private LinearLayout profilesContainer;
    private TextView activationStatus;
    private TextView ramInfo;
    private EditText intervalInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        requestNotificationPermission();
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(40));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(text("LEO OPTIMAZER", 27, TEXT, true));
        TextView subtitle = text("Brevent Core • perfis por aplicativo", 14, MUTED, false);
        subtitle.setPadding(0, dp(2), 0, dp(18));
        root.addView(subtitle);

        LinearLayout statusCard = card();
        activationStatus = text("Verificando ativação Brevent…", 16, TEXT, true);
        statusCard.addView(activationStatus);
        statusCard.addView(spacer(10));
        statusCard.addView(button("VERIFICAR ATIVAÇÃO", v -> refreshActivationStatus()));
        statusCard.addView(spacer(8));
        statusCard.addView(button("GERENCIAR ATIVAÇÃO BREVENT", v -> startActivity(new Intent(this, ActivationActivity.class))));
        TextView hint = text("O modo principal usa as permissões concedidas pelo Brevent. Não é necessário manter um Bridge ADB rodando.", 12, MUTED, false);
        hint.setPadding(0, dp(10), 0, 0);
        statusCard.addView(hint);
        root.addView(statusCard);

        root.addView(sectionTitle("SERVIÇO AUTOMÁTICO"));
        LinearLayout serviceCard = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button start = button("INICIAR", v -> startOptimizer());
        Button stop = button("PARAR", v -> stopOptimizer());
        row.addView(start, new LinearLayout.LayoutParams(0, dp(48), 1f));
        row.addView(spacerHorizontal(8));
        row.addView(stop, new LinearLayout.LayoutParams(0, dp(48), 1f));
        serviceCard.addView(row);
        root.addView(serviceCard);

        root.addView(sectionTitle("LIMPEZA DE RAM"));
        LinearLayout ramCard = card();
        ramInfo = text("RAM: —", 15, TEXT, true);
        ramCard.addView(ramInfo);
        ramCard.addView(spacer(10));

        TextView intervalLabel = text("Intervalo em segundos (0 = desligado, mínimo 10s)", 12, MUTED, false);
        ramCard.addView(intervalLabel);
        intervalInput = editText("0");
        intervalInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        ramCard.addView(intervalInput, matchWrap());
        ramCard.addView(spacer(8));
        ramCard.addView(button("SALVAR INTERVALO", v -> saveInterval()));
        ramCard.addView(spacer(8));
        ramCard.addView(button("LIMPAR RAM AGORA", v -> cleanRamNow()));
        root.addView(ramCard);

        root.addView(sectionTitle("PERFIS POR APLICATIVO"));
        root.addView(button("+ ADICIONAR APLICATIVO", v -> chooseApplication()),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        root.addView(spacer(10));

        profilesContainer = new LinearLayout(this);
        profilesContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(profilesContainer, matchWrap());

        TextView safety = text("Segurança: ao sair de um aplicativo com perfil, o Leo Optimazer restaura a resolução e a densidade que estavam ativas antes do perfil.", 12, MUTED, false);
        safety.setPadding(0, dp(16), 0, 0);
        root.addView(safety);

        setContentView(scroll);
    }

    private void refreshAll() {
        SharedPreferences prefs = getSharedPreferences(MonitorService.PREFS, MODE_PRIVATE);
        intervalInput.setText(String.valueOf(prefs.getLong(MonitorService.KEY_INTERVAL_SEC, 0L)));
        refreshActivationStatus();
        refreshMemory();
        renderProfiles();
    }

    private void refreshActivationStatus() {
        PrivilegedOps ops = new PrivilegedOps(this);
        boolean active = ops.isActivated();
        activationStatus.setText(active ? "● BREVENT ATIVADO" : "● ATIVAÇÃO BREVENT INCOMPLETA");
        activationStatus.setTextColor(active ? GOOD : BAD);
    }

    private void startOptimizer() {
        PrivilegedOps ops = new PrivilegedOps(this);
        if (!ops.isActivated()) {
            Toast.makeText(this, "Conclua a ativação pelo Brevent e libere o Acesso ao uso", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, ActivationActivity.class));
            return;
        }

        Intent intent = new Intent(this, MonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
        Toast.makeText(this, "Leo Optimazer iniciado", Toast.LENGTH_SHORT).show();
    }

    private void stopOptimizer() {
        stopService(new Intent(this, MonitorService.class));
        Toast.makeText(this, "Monitoramento parado", Toast.LENGTH_SHORT).show();
    }

    private void saveInterval() {
        try {
            long sec = Long.parseLong(intervalInput.getText().toString().trim());
            if (sec != 0 && sec < 10) {
                Toast.makeText(this, "Use 0 ou pelo menos 10 segundos", Toast.LENGTH_LONG).show();
                return;
            }
            getSharedPreferences(MonitorService.PREFS, MODE_PRIVATE).edit()
                    .putLong(MonitorService.KEY_INTERVAL_SEC, sec)
                    .apply();
            Toast.makeText(this, sec == 0 ? "Limpeza programada desativada" : "Intervalo salvo: " + sec + "s", Toast.LENGTH_SHORT).show();
            if (getSharedPreferences(MonitorService.PREFS, MODE_PRIVATE).getBoolean(MonitorService.KEY_ENABLED, false)) {
                Intent intent = new Intent(this, MonitorService.class);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Intervalo inválido", Toast.LENGTH_SHORT).show();
        }
    }

    private void cleanRamNow() {
        new Thread(() -> {
            long before = availableMemoryMb();
            try {
                String result = BridgeClient.send("KILL_CACHED");
                Thread.sleep(500L);
                long after = availableMemoryMb();
                long freed = Math.max(0, after - before);
                getSharedPreferences(MonitorService.PREFS, MODE_PRIVATE).edit()
                        .putLong(MonitorService.KEY_LAST_CLEANUP, System.currentTimeMillis())
                        .putLong(MonitorService.KEY_LAST_FREED_MB, freed)
                        .apply();
                runOnUiThread(() -> {
                    refreshMemory();
                    Toast.makeText(this, "Limpeza concluída • +" + freed + " MB disponíveis", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Não foi possível limpar a RAM" : e.getMessage();
                runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
            }
        }, "Leo-Ram-Clean").start();
    }

    private void refreshMemory() {
        SharedPreferences prefs = getSharedPreferences(MonitorService.PREFS, MODE_PRIVATE);
        long available = availableMemoryMb();
        long lastFreed = prefs.getLong(MonitorService.KEY_LAST_FREED_MB, 0L);
        long last = prefs.getLong(MonitorService.KEY_LAST_CLEANUP, 0L);
        String lastText = last == 0 ? "ainda não executada" : android.text.format.DateFormat.format("HH:mm:ss", last).toString();
        ramInfo.setText("Disponível: " + available + " MB\nÚltima limpeza: " + lastText + " • liberado: " + lastFreed + " MB");
    }

    private long availableMemoryMb() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        return info.availMem / (1024L * 1024L);
    }

    private void chooseApplication() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> all = pm.getInstalledApplications(0);
            List<ApplicationInfo> launchable = new ArrayList<>();
            for (ApplicationInfo info : all) {
                if (info.packageName.equals(getPackageName())) continue;
                if (pm.getLaunchIntentForPackage(info.packageName) != null) launchable.add(info);
            }
            Collections.sort(launchable, Comparator.comparing(a -> pm.getApplicationLabel(a).toString().toLowerCase()));
            String[] labels = new String[launchable.size()];
            for (int i = 0; i < launchable.size(); i++) {
                ApplicationInfo info = launchable.get(i);
                labels[i] = pm.getApplicationLabel(info) + "\n" + info.packageName;
            }
            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle("Escolher aplicativo")
                    .setItems(labels, (dialog, which) -> editProfile(launchable.get(which).packageName))
                    .setNegativeButton("Cancelar", null)
                    .show());
        }, "Leo-App-List").start();
    }

    private void editProfile(String packageName) {
        ProfileStore.Profile existing = ProfileStore.get(this, packageName);
        DisplayMetrics dm = getResources().getDisplayMetrics();

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(8), dp(22), 0);

        EditText width = editText(String.valueOf(existing == null ? dm.widthPixels : existing.width));
        width.setHint("Largura");
        width.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText height = editText(String.valueOf(existing == null ? dm.heightPixels : existing.height));
        height.setHint("Altura");
        height.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText density = editText(String.valueOf(existing == null ? dm.densityDpi : existing.density));
        density.setHint("DPI");
        density.setInputType(InputType.TYPE_CLASS_NUMBER);
        CheckBox enabled = new CheckBox(this);
        enabled.setText("Perfil ativado");
        enabled.setTextColor(TEXT);
        enabled.setChecked(existing == null || existing.enabled);

        form.addView(label("Largura (320 a 7680 px)"));
        form.addView(width);
        form.addView(label("Altura (320 a 7680 px)"));
        form.addView(height);
        form.addView(label("DPI (80 a 4000 — ex.: 1600)"));
        form.addView(density);
        form.addView(enabled);

        new AlertDialog.Builder(this)
                .setTitle(packageName)
                .setView(form)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    try {
                        int w = Integer.parseInt(width.getText().toString().trim());
                        int h = Integer.parseInt(height.getText().toString().trim());
                        int d = Integer.parseInt(density.getText().toString().trim());
                        if (w < 320 || w > 7680 || h < 320 || h > 7680 || d < 80 || d > 4000) {
                            throw new IllegalArgumentException();
                        }
                        ProfileStore.save(this, new ProfileStore.Profile(packageName, w, h, d, true, enabled.isChecked()));
                        renderProfiles();
                        Toast.makeText(this, "Perfil salvo: " + w + " × " + h + " • " + d + " DPI", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Use resolução entre 320–7680 px e DPI entre 80–4000", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void renderProfiles() {
        profilesContainer.removeAllViews();
        List<ProfileStore.Profile> profiles = ProfileStore.all(this);
        if (profiles.isEmpty()) {
            profilesContainer.addView(text("Nenhum perfil criado.", 14, MUTED, false));
            return;
        }

        PackageManager pm = getPackageManager();
        for (ProfileStore.Profile profile : profiles) {
            LinearLayout box = card();
            String labelName = profile.packageName;
            try {
                ApplicationInfo info = pm.getApplicationInfo(profile.packageName, 0);
                labelName = pm.getApplicationLabel(info).toString();
            } catch (Exception ignored) {}

            box.addView(text(labelName, 16, TEXT, true));
            TextView detail = text(profile.packageName + "\n" + profile.width + " × " + profile.height + " • " + profile.density + " DPI • " + (profile.enabled ? "ATIVO" : "PAUSADO"), 12, MUTED, false);
            detail.setPadding(0, dp(3), 0, dp(10));
            box.addView(detail);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button edit = button("EDITAR", v -> editProfile(profile.packageName));
            Button delete = button("REMOVER", v -> new AlertDialog.Builder(this)
                    .setTitle("Remover perfil?")
                    .setMessage(profile.packageName)
                    .setPositiveButton("Remover", (d, w) -> {
                        ProfileStore.delete(this, profile.packageName);
                        renderProfiles();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show());
            actions.addView(edit, new LinearLayout.LayoutParams(0, dp(44), 1f));
            actions.addView(spacerHorizontal(8));
            actions.addView(delete, new LinearLayout.LayoutParams(0, dp(44), 1f));
            box.addView(actions);
            profilesContainer.addView(box);
            profilesContainer.addView(spacer(9));
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private TextView sectionTitle(String value) {
        TextView tv = text(value, 13, ACCENT, true);
        tv.setPadding(0, dp(20), 0, dp(8));
        return tv;
    }

    private TextView label(String value) {
        TextView tv = text(value, 12, MUTED, false);
        tv.setPadding(0, dp(8), 0, dp(3));
        return tv;
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(15), dp(15), dp(15), dp(15));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), CARD_2);
        layout.setBackground(bg);
        layout.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(4);
        layout.setLayoutParams(lp);
        return layout;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(TEXT);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD_2);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.rgb(47, 69, 91));
        b.setBackground(bg);
        b.setOnClickListener(listener);
        return b;
    }

    private EditText editText(String value) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setSingleLine(true);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD_2);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.rgb(47, 69, 91));
        e.setBackground(bg);
        return e;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        return tv;
    }

    private View spacer(int heightDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return v;
    }

    private View spacerHorizontal(int widthDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), 1));
        return v;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
