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
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
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
    private static final int WARN = Color.rgb(255, 193, 92);

    private LinearLayout profilesContainer;
    private TextView activationStatus;
    private TextView ramInfo;
    private EditText intervalInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        requestNotificationPermission();
        ShizukuCore.bindUserService();
        ensureProfileMonitor();
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ShizukuCore.bindUserService();
        ensureProfileMonitor();
        refreshAll();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(40));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(text("LEO OPTIMAZER", 27, TEXT, true));
        TextView subtitle = text("Shizuku Core • DPI + resolução + Touch Engine por aplicativo", 14, MUTED, false);
        subtitle.setPadding(0, dp(2), 0, dp(18));
        root.addView(subtitle);

        LinearLayout statusCard = card();
        activationStatus = text("Verificando núcleo Shizuku…", 16, TEXT, true);
        statusCard.addView(activationStatus);
        statusCard.addView(spacer(10));
        statusCard.addView(button("VERIFICAR SHIZUKU", v -> refreshActivationStatus()));
        statusCard.addView(spacer(8));
        statusCard.addView(button("GERENCIAR SHIZUKU", v -> startActivity(new Intent(this, ActivationActivity.class))));
        TextView hint = text(
                "400 DPI é o padrão de referência. DPI maior aumenta a resolução vinculada suavemente; DPI menor reduz. O Touch Engine é ativado só enquanto o app do perfil está em primeiro plano e restaura os ajustes ao sair.",
                12, MUTED, false);
        hint.setPadding(0, dp(10), 0, 0);
        statusCard.addView(hint);
        root.addView(statusCard);

        root.addView(sectionTitle("LIMPEZA AUTOMÁTICA DE RAM"));
        LinearLayout ramCard = card();
        ramInfo = text("RAM: —", 14, TEXT, true);
        ramCard.addView(ramInfo);
        ramCard.addView(spacer(10));
        ramCard.addView(text("A limpeza usa o núcleo Shizuku. Desligar a RAM automática não desliga DPI, resolução nem Touch Engine.", 12, MUTED, false));
        ramCard.addView(spacer(10));
        ramCard.addView(text("Intervalo em segundos (0 = desligado, mínimo 10s)", 12, MUTED, false));
        intervalInput = editText("0");
        intervalInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        ramCard.addView(intervalInput, matchWrap());
        ramCard.addView(spacer(8));
        ramCard.addView(button("SALVAR INTERVALO", v -> saveIntervalAndStart()));
        ramCard.addView(spacer(8));
        ramCard.addView(button("LIMPAR RAM AGORA", v -> cleanRamNow()));
        ramCard.addView(spacer(8));
        ramCard.addView(button("DESLIGAR RAM AUTOMÁTICA", v -> stopOptimizer()));
        root.addView(ramCard);

        root.addView(sectionTitle("PERFIS INDIVIDUAIS"));
        root.addView(button("+ ADICIONAR APLICATIVO", v -> chooseApplication()),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        root.addView(spacer(10));

        profilesContainer = new LinearLayout(this);
        profilesContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(profilesContainer, matchWrap());

        TextView safety = text(
                "Touch Engine: Resposta rápida prioriza modo desempenho e taxa de atualização disponível. Arrasto linear usa a suavização/estabilidade do controlador de toque do fabricante quando o Shizuku consegue acessá-la. O Leo não injeta uma segunda sequência de toques se o aparelho não oferecer esse recurso.",
                12, MUTED, false);
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
        boolean alive = ShizukuCore.isBinderAlive();
        boolean permission = ShizukuCore.hasPermission();
        boolean ready = ShizukuCore.isReady();

        if (ready) {
            int uid = ShizukuCore.getServiceUid();
            activationStatus.setText(uid == 0 ? "● SHIZUKU ROOT ATIVO" : "● SHIZUKU SHELL ATIVO");
            activationStatus.setTextColor(GOOD);
        } else if (permission && alive) {
            activationStatus.setText("● SHIZUKU AUTORIZADO • CONECTANDO NÚCLEO");
            activationStatus.setTextColor(WARN);
            ShizukuCore.bindUserService();
        } else if (alive) {
            activationStatus.setText("● SHIZUKU ATIVO • PERMISSÃO PENDENTE");
            activationStatus.setTextColor(BAD);
        } else {
            activationStatus.setText("● SHIZUKU PARADO");
            activationStatus.setTextColor(BAD);
        }
    }

    private boolean requireShizuku() {
        if (!ShizukuCore.isBinderAlive() || !ShizukuCore.hasPermission()) {
            Toast.makeText(this, "Inicie e autorize o Shizuku primeiro", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, ActivationActivity.class));
            return false;
        }
        ShizukuCore.bindUserService();
        return true;
    }

    private void ensureProfileMonitor() {
        if (ProfileStore.all(this).isEmpty()) return;
        try {
            Intent intent = new Intent(this, MonitorService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
        } catch (Throwable ignored) {}
    }

    private void saveIntervalAndStart() {
        try {
            long sec = Long.parseLong(intervalInput.getText().toString().trim());
            if (sec != 0 && sec < 10L) {
                Toast.makeText(this, "Use 0 ou pelo menos 10 segundos", Toast.LENGTH_LONG).show();
                return;
            }
            getSharedPreferences(MonitorService.PREFS, MODE_PRIVATE).edit()
                    .putLong(MonitorService.KEY_INTERVAL_SEC, sec).apply();
            ensureProfileMonitor();
            if (sec > 0) {
                if (!requireShizuku()) return;
                Intent intent = new Intent(this, MonitorService.class);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
                Toast.makeText(this, "RAM automática: a cada " + sec + "s", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "RAM automática desligada • perfis continuam ativos", Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Intervalo inválido", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopOptimizer() {
        getSharedPreferences(MonitorService.PREFS, MODE_PRIVATE).edit()
                .putLong(MonitorService.KEY_INTERVAL_SEC, 0L).apply();
        intervalInput.setText("0");
        ensureProfileMonitor();
        Toast.makeText(this, "RAM automática desligada • monitor de perfis mantido", Toast.LENGTH_SHORT).show();
    }

    private void cleanRamNow() {
        if (!requireShizuku()) return;
        new Thread(() -> {
            long before = availableMemoryMb();
            try {
                ShizukuCore.execute("am kill-all");
                Thread.sleep(500L);
                long after = availableMemoryMb();
                long freed = Math.max(0L, after - before);
                getSharedPreferences(MonitorService.PREFS, MODE_PRIVATE).edit()
                        .putLong(MonitorService.KEY_LAST_CLEANUP, System.currentTimeMillis())
                        .putLong(MonitorService.KEY_LAST_FREED_MB, freed)
                        .putString(MonitorService.KEY_LAST_CLEANUP_RESULT, "SHIZUKU_OK")
                        .apply();
                runOnUiThread(() -> {
                    refreshMemory();
                    Toast.makeText(this, "Limpeza concluída • +" + freed + " MB disponíveis", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                String message = safeMessage(e);
                runOnUiThread(() -> Toast.makeText(this, "Falha Shizuku: " + message, Toast.LENGTH_LONG).show());
            }
        }, "Leo-Shizuku-Ram").start();
    }

    private void refreshMemory() {
        SharedPreferences prefs = getSharedPreferences(MonitorService.PREFS, MODE_PRIVATE);
        long available = availableMemoryMb();
        long lastFreed = prefs.getLong(MonitorService.KEY_LAST_FREED_MB, 0L);
        long last = prefs.getLong(MonitorService.KEY_LAST_CLEANUP, 0L);
        String result = prefs.getString(MonitorService.KEY_LAST_CLEANUP_RESULT, "—");
        String profileResult = prefs.getString(MonitorService.KEY_LAST_PROFILE_RESULT, "aguardando app com perfil");
        String touch = prefs.getString(MonitorService.KEY_LAST_TOUCH_RESULT, "aguardando app");
        String lastText = last == 0 ? "ainda não executada" : android.text.format.DateFormat.format("HH:mm:ss", last).toString();
        ramInfo.setText("Disponível: " + available + " MB\nÚltima limpeza: " + lastText + " • Δ +" + lastFreed
                + " MB\nRAM: " + result + "\nPerfil: " + profileResult + "\nTouch: " + touch);
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
        EditText height = editText(String.valueOf(existing == null ? dm.heightPixels : existing.height));
        EditText density = editText(String.valueOf(existing == null ? PerAppCompat.DEFAULT_DEDICATED_DPI : existing.density));
        EditText touchLevel = editText(String.valueOf(existing == null ? 85 : existing.touchLevel));
        width.setInputType(InputType.TYPE_CLASS_NUMBER);
        height.setInputType(InputType.TYPE_CLASS_NUMBER);
        density.setInputType(InputType.TYPE_CLASS_NUMBER);
        touchLevel.setInputType(InputType.TYPE_CLASS_NUMBER);

        CheckBox enabled = check("Perfil individual ativado", existing == null || existing.enabled);
        CheckBox fastTouch = check("Resposta rápida ao toque", existing == null || existing.fastTouch);
        CheckBox linearDrag = check("Arrasto linear / suavização", existing == null || existing.linearDrag);

        TextView preview = text("Calculando perfil…", 12, ACCENT, true);
        preview.setPadding(0, dp(8), 0, dp(5));

        form.addView(label("Resolução-base @ 400 DPI — largura"));
        form.addView(width);
        form.addView(label("Resolução-base @ 400 DPI — altura"));
        form.addView(height);
        form.addView(label("DPI dedicada Android (400 = padrão)"));
        form.addView(density);
        form.addView(preview);
        form.addView(enabled);
        form.addView(fastTouch);
        form.addView(linearDrag);
        form.addView(label("Intensidade do Touch Engine (1–100)"));
        form.addView(touchLevel);
        form.addView(text("Resposta rápida pode alterar temporariamente Game Mode e taxa mínima de atualização do Android. Arrasto linear tenta usar a estabilização do controlador touch do fabricante; tudo é restaurado ao sair do aplicativo.", 11, MUTED, false));

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePreview(width, height, density, preview, dm, packageName);
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        width.addTextChangedListener(watcher);
        height.addTextChangedListener(watcher);
        density.addTextChangedListener(watcher);
        updatePreview(width, height, density, preview, dm, packageName);

        new AlertDialog.Builder(this)
                .setTitle(packageName)
                .setView(form)
                .setPositiveButton("SALVAR E APLICAR", (dialog, which) -> {
                    try {
                        int w = Integer.parseInt(width.getText().toString().trim());
                        int h = Integer.parseInt(height.getText().toString().trim());
                        int d = Integer.parseInt(density.getText().toString().trim());
                        int level = Integer.parseInt(touchLevel.getText().toString().trim());
                        if (w < 320 || w > 7680 || h < 320 || h > 7680
                                || d < PerAppCompat.MIN_DEDICATED_DPI || d > PerAppCompat.MAX_DEDICATED_DPI
                                || level < 1 || level > 100) throw new IllegalArgumentException();
                        ProfileStore.Profile profile = new ProfileStore.Profile(
                                packageName, w, h, d, true, enabled.isChecked(),
                                fastTouch.isChecked(), linearDrag.isChecked(), level);
                        applyAndSaveProfile(profile);
                    } catch (Exception e) {
                        Toast.makeText(this, "Use resolução 320–7680, DPI "
                                + PerAppCompat.MIN_DEDICATED_DPI + "–" + PerAppCompat.MAX_DEDICATED_DPI
                                + " e Touch 1–100", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void updatePreview(EditText width, EditText height, EditText density,
                               TextView output, DisplayMetrics metrics, String packageName) {
        try {
            int w = Integer.parseInt(width.getText().toString().trim());
            int h = Integer.parseInt(height.getText().toString().trim());
            int d = Integer.parseInt(density.getText().toString().trim());
            PerAppCompat.DpiLimits limits = PerAppCompat.limitsForResolution(w, h, metrics);
            int normalized = limits.clamp(d);
            int linkedW = PerAppCompat.linkedWidth(w, normalized);
            int linkedH = PerAppCompat.linkedHeight(h, normalized);
            ProfileStore.Profile preview = new ProfileStore.Profile(packageName, w, h, normalized, true, true);
            PerAppCompat.Plan plan = PerAppCompat.build(preview, metrics);
            String adjusted = d == normalized ? "" : " • limitado para " + normalized;
            output.setText(limits.label() + adjusted
                    + "\nResolução vinculada pela DPI: " + linkedW + " × " + linkedH
                    + "\nAplicação Android aproximada: " + plan.estimatedWidth + " × " + plan.estimatedHeight);
            output.setTextColor(d == normalized ? GOOD : WARN);
        } catch (Exception e) {
            output.setText("Digite resolução e DPI válidas para calcular o perfil.");
            output.setTextColor(MUTED);
        }
    }

    private void applyAndSaveProfile(ProfileStore.Profile profile) {
        if (!requireShizuku()) return;
        PerAppCompat.Plan plan = PerAppCompat.build(profile, getResources().getDisplayMetrics());
        new Thread(() -> {
            try {
                ShizukuCore.execute(plan.command);
                ProfileStore.save(this, profile);
                ensureProfileMonitor();
                runOnUiThread(() -> {
                    renderProfiles();
                    Toast.makeText(this, "Perfil salvo. Abra o aplicativo para ativar DPI e Touch Engine.\n" + plan.summary, Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                String message = safeMessage(e);
                runOnUiThread(() -> Toast.makeText(this, "Não foi possível aplicar: " + message, Toast.LENGTH_LONG).show());
            }
        }, "Leo-Apply-Profile").start();
    }

    private void reapplyProfile(ProfileStore.Profile profile) {
        if (!requireShizuku()) return;
        PerAppCompat.Plan plan = PerAppCompat.build(profile, getResources().getDisplayMetrics());
        new Thread(() -> {
            try {
                ShizukuCore.execute(plan.command);
                runOnUiThread(() -> Toast.makeText(this, "Resolução reaplicada. Abra o app para o Touch Engine.", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                String message = safeMessage(e);
                runOnUiThread(() -> Toast.makeText(this, "Falha ao reaplicar: " + message, Toast.LENGTH_LONG).show());
            }
        }, "Leo-Reapply-Profile").start();
    }

    private void removeProfile(ProfileStore.Profile profile) {
        if (!requireShizuku()) return;
        new Thread(() -> {
            try {
                try { ShizukuCore.execute("leo touch-reset " + profile.packageName); } catch (Exception ignored) {}
                ShizukuCore.execute(PerAppCompat.resetCommand(profile.packageName));
                ProfileStore.delete(this, profile.packageName);
                runOnUiThread(() -> {
                    renderProfiles();
                    Toast.makeText(this, "Perfil removido e ajustes restaurados", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                String message = safeMessage(e);
                runOnUiThread(() -> Toast.makeText(this, "Não foi possível restaurar: " + message, Toast.LENGTH_LONG).show());
            }
        }, "Leo-Remove-Profile").start();
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
            String name = profile.packageName;
            try {
                ApplicationInfo info = pm.getApplicationInfo(profile.packageName, 0);
                name = pm.getApplicationLabel(info).toString();
            } catch (Exception ignored) {}

            PerAppCompat.Plan plan = PerAppCompat.build(profile, metrics);
            box.addView(text(name, 16, TEXT, true));
            box.addView(text(
                    profile.packageName
                            + "\nBase: " + profile.width + " × " + profile.height + " @ 400 DPI"
                            + "\nDPI dedicada: " + profile.density
                            + "\nEfetivo aproximado: " + plan.estimatedWidth + " × " + plan.estimatedHeight
                            + "\nResposta rápida: " + (profile.fastTouch ? "ON" : "OFF")
                            + " • Arrasto linear: " + (profile.linearDrag ? "ON" : "OFF")
                            + " • Intensidade: " + profile.touchLevel
                            + "\nEstado: " + (profile.enabled ? "ATIVO AO ABRIR O APP" : "PAUSADO"),
                    12, MUTED, false));
            box.addView(spacer(10));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(button("EDITAR", v -> editProfile(profile.packageName)), new LinearLayout.LayoutParams(0, dp(44), 1f));
            row.addView(spacerHorizontal(8));
            row.addView(button("REAPLICAR", v -> reapplyProfile(profile)), new LinearLayout.LayoutParams(0, dp(44), 1f));
            box.addView(row);
            box.addView(spacer(8));
            box.addView(button("REMOVER E RESTAURAR PADRÃO", v -> new AlertDialog.Builder(this)
                    .setTitle("Remover perfil?")
                    .setMessage("Resolução, DPI e Touch Engine de " + profile.packageName + " serão restaurados.")
                    .setPositiveButton("Remover", (d, w) -> removeProfile(profile))
                    .setNegativeButton("Cancelar", null).show()));

            profilesContainer.addView(box);
            profilesContainer.addView(spacer(9));
        }
    }

    private CheckBox check(String label, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(TEXT);
        box.setChecked(checked);
        return box;
    }

    private String safeMessage(Throwable t) {
        String message = t.getMessage();
        return message == null || message.trim().isEmpty() ? t.getClass().getSimpleName() : message;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
