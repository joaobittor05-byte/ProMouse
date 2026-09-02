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
import android.os.Handler;
import android.os.Looper;
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

    private static final String KEY_RAM_PENDING = "ram_brevent_pending";
    private static final String KEY_RAM_BEFORE = "ram_brevent_before";

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
        finishPendingRamMeasurement();
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
        TextView subtitle = text("Brevent Core • escala REAL por aplicativo", 14, MUTED, false);
        subtitle.setPadding(0, dp(2), 0, dp(18));
        root.addView(subtitle);

        LinearLayout statusCard = card();
        activationStatus = text("Verificando Brevent…", 16, TEXT, true);
        statusCard.addView(activationStatus);
        statusCard.addView(spacer(10));
        statusCard.addView(button("VERIFICAR ATIVAÇÃO", v -> refreshActivationStatus()));
        statusCard.addView(spacer(8));
        statusCard.addView(button("GERENCIAR ATIVAÇÃO BREVENT", v -> startActivity(new Intent(this, ActivationActivity.class))));
        TextView hint = text("Os perfis novos não usam mais wm size/wm density global. O Leo usa a escala de compatibilidade por pacote do Android através do Brevent.", 12, MUTED, false);
        hint.setPadding(0, dp(10), 0, 0);
        statusCard.addView(hint);
        root.addView(statusCard);

        root.addView(sectionTitle("SERVIÇO DE INTERVALO"));
        LinearLayout serviceCard = card();
        TextView serviceInfo = text("Os perfis individuais ficam gravados no Android e não precisam deste serviço. O serviço mantém o temporizador de RAM; a limpeza privilegiada é confirmada pelo Brevent.", 12, MUTED, false);
        serviceInfo.setPadding(0, 0, 0, dp(10));
        serviceCard.addView(serviceInfo);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(button("INICIAR", v -> startOptimizer()), new LinearLayout.LayoutParams(0, dp(48), 1f));
        row.addView(spacerHorizontal(8));
        row.addView(button("PARAR", v -> stopOptimizer()), new LinearLayout.LayoutParams(0, dp(48), 1f));
        serviceCard.addView(row);
        root.addView(serviceCard);

        root.addView(sectionTitle("LIMPEZA REAL DE RAM"));
        LinearLayout ramCard = card();
        ramInfo = text("RAM: —", 15, TEXT, true);
        ramCard.addView(ramInfo);
        ramCard.addView(spacer(10));
        ramCard.addView(text("O botão abaixo executa am kill-all como shell através do Brevent. Apps em primeiro plano não são encerrados.", 12, MUTED, false));
        ramCard.addView(spacer(10));
        ramCard.addView(text("Intervalo em segundos (0 = desligado, mínimo 10s)", 12, MUTED, false));
        intervalInput = editText("0");
        intervalInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        ramCard.addView(intervalInput, matchWrap());
        ramCard.addView(spacer(8));
        ramCard.addView(button("SALVAR INTERVALO", v -> saveInterval()));
        ramCard.addView(spacer(8));
        ramCard.addView(button("LIMPAR RAM AGORA PELO BREVENT", v -> cleanRamNow()));
        root.addView(ramCard);

        root.addView(sectionTitle("PERFIS INDIVIDUAIS REAIS"));
        root.addView(button("+ ADICIONAR APLICATIVO", v -> chooseApplication()),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        root.addView(spacer(10));

        profilesContainer = new LinearLayout(this);
        profilesContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(profilesContainer, matchWrap());

        TextView safety = text("Cada perfil usa am compat somente no pacote selecionado. O display do sistema e os outros aplicativos não recebem wm size/wm density.", 12, MUTED, false);
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
        boolean permissionActive = ops.isActivated();
        boolean commandApi = BreventCommand.isAvailable(this);
        boolean active = permissionActive && commandApi;
        activationStatus.setText(active
                ? "● BREVENT ATIVADO • COMMAND API OK"
                : "● BREVENT INCOMPLETO");
        activationStatus.setTextColor(active ? GOOD : BAD);
    }

    private void startOptimizer() {
        Intent intent = new Intent(this, MonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
        Toast.makeText(this, "Temporizador iniciado", Toast.LENGTH_SHORT).show();
    }

    private void stopOptimizer() {
        stopService(new Intent(this, MonitorService.class));
        Toast.makeText(this, "Temporizador parado", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, sec == 0 ? "Intervalo desativado" : "Intervalo salvo: " + sec + "s", Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Intervalo inválido", Toast.LENGTH_SHORT).show();
        }
    }

    private void cleanRamNow() {
        if (!BreventCommand.isAvailable(this)) {
            Toast.makeText(this, "Brevent Command não foi encontrado", Toast.LENGTH_LONG).show();
            return;
        }

        long before = availableMemoryMb();
        getSharedPreferences(MonitorService.PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_RAM_PENDING, true)
                .putLong(KEY_RAM_BEFORE, before)
                .apply();

        if (!BreventCommand.execute(this, BreventCommand.ramCleanupCommand())) {
            getSharedPreferences(MonitorService.PREFS, MODE_PRIVATE).edit().putBoolean(KEY_RAM_PENDING, false).apply();
            Toast.makeText(this, "Não foi possível abrir o executor do Brevent", Toast.LENGTH_LONG).show();
        }
    }

    private void finishPendingRamMeasurement() {
        SharedPreferences prefs = getSharedPreferences(MonitorService.PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_RAM_PENDING, false)) return;
        long before = prefs.getLong(KEY_RAM_BEFORE, availableMemoryMb());
        prefs.edit().putBoolean(KEY_RAM_PENDING, false).apply();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            long after = availableMemoryMb();
            long freed = Math.max(0L, after - before);
            getSharedPreferences(MonitorService.PREFS, MODE_PRIVATE).edit()
                    .putLong(MonitorService.KEY_LAST_CLEANUP, System.currentTimeMillis())
                    .putLong(MonitorService.KEY_LAST_FREED_MB, freed)
                    .apply();
            refreshMemory();
            Toast.makeText(this, "Brevent executado • RAM disponível: " + after + " MB (Δ +" + freed + " MB)", Toast.LENGTH_LONG).show();
        }, 700L);
    }

    private void refreshMemory() {
        SharedPreferences prefs = getSharedPreferences(MonitorService.PREFS, MODE_PRIVATE);
        long available = availableMemoryMb();
        long lastFreed = prefs.getLong(MonitorService.KEY_LAST_FREED_MB, 0L);
        long last = prefs.getLong(MonitorService.KEY_LAST_CLEANUP, 0L);
        String lastText = last == 0 ? "ainda não executada" : android.text.format.DateFormat.format("HH:mm:ss", last).toString();
        ramInfo.setText("Disponível: " + available + " MB\nÚltima limpeza: " + lastText + " • variação: +" + lastFreed + " MB");
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
        enabled.setText("Perfil individual ativado");
        enabled.setTextColor(TEXT);
        enabled.setChecked(existing == null || existing.enabled);

        form.addView(label("Resolução alvo — largura"));
        form.addView(width);
        form.addView(label("Resolução alvo — altura"));
        form.addView(height);
        form.addView(label("DPI alvo (o Android usa a escala individual mais próxima)"));
        form.addView(density);
        form.addView(enabled);

        new AlertDialog.Builder(this)
                .setTitle(packageName)
                .setView(form)
                .setPositiveButton("SALVAR E APLICAR", (dialog, which) -> {
                    try {
                        int w = Integer.parseInt(width.getText().toString().trim());
                        int h = Integer.parseInt(height.getText().toString().trim());
                        int d = Integer.parseInt(density.getText().toString().trim());
                        if (w < 320 || w > 7680 || h < 320 || h > 7680 || d < 80 || d > 4000) throw new IllegalArgumentException();
                        ProfileStore.Profile profile = new ProfileStore.Profile(packageName, w, h, d, true, enabled.isChecked());
                        ProfileStore.save(this, profile);
                        renderProfiles();
                        applyProfile(profile);
                    } catch (Exception e) {
                        Toast.makeText(this, "Use resolução 320–7680 e DPI 80–4000", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void applyProfile(ProfileStore.Profile profile) {
        PerAppCompat.Plan plan = PerAppCompat.build(profile, getResources().getDisplayMetrics());
        if (!BreventCommand.execute(this, plan.command)) {
            Toast.makeText(this, "Brevent Command não disponível", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, plan.summary + " • o app será fechado para aplicar", Toast.LENGTH_LONG).show();
    }

    private void removeProfile(ProfileStore.Profile profile) {
        String reset = PerAppCompat.resetCommand(profile.packageName);
        ProfileStore.delete(this, profile.packageName);
        renderProfiles();
        if (!BreventCommand.execute(this, reset)) {
            Toast.makeText(this, "Perfil local removido. Abra o Brevent para restaurar a escala do pacote.", Toast.LENGTH_LONG).show();
        }
    }

    private void renderProfiles() {
        profilesContainer.removeAllViews();
        List<ProfileStore.Profile> profiles = ProfileStore.all(this);
        if (profiles.isEmpty()) {
            profilesContainer.addView(text("Nenhum perfil criado.", 14, MUTED, false));
            return;
        }

        PackageManager pm = getPackageManager();
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        for (ProfileStore.Profile profile : profiles) {
            LinearLayout box = card();
            String labelName = profile.packageName;
            try {
                ApplicationInfo info = pm.getApplicationInfo(profile.packageName, 0);
                labelName = pm.getApplicationLabel(info).toString();
            } catch (Exception ignored) {}

            box.addView(text(labelName, 16, TEXT, true));
            PerAppCompat.Plan plan = PerAppCompat.build(profile, metrics);
            TextView detail = text(profile.packageName + "\nAlvo: " + profile.width + " × " + profile.height + " • " + profile.density + " DPI\n" + plan.summary + " • " + (profile.enabled ? "ATIVO" : "PAUSADO"), 12, MUTED, false);
            detail.setPadding(0, dp(3), 0, dp(10));
            box.addView(detail);

            box.addView(button("APLICAR NO BREVENT", v -> applyProfile(profile)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
            box.addView(spacer(7));
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.addView(button("EDITAR", v -> editProfile(profile.packageName)), new LinearLayout.LayoutParams(0, dp(44), 1f));
            actions.addView(spacerHorizontal(8));
            actions.addView(button("REMOVER + RESTAURAR", v -> new AlertDialog.Builder(this)
                    .setTitle("Remover perfil?")
                    .setMessage("Também vou mandar o Brevent restaurar a escala padrão somente de " + profile.packageName)
                    .setPositiveButton("Remover", (d, w) -> removeProfile(profile))
                    .setNegativeButton("Cancelar", null)
                    .show()), new LinearLayout.LayoutParams(0, dp(44), 1f));
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
