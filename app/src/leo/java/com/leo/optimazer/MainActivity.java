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
    private static final int BG = LeoUi.BG;
    private static final int CARD = LeoUi.CARD;
    private static final int CARD_2 = LeoUi.SURFACE;
    private static final int TEXT = LeoUi.TEXT;
    private static final int MUTED = LeoUi.MUTED;
    private static final int ACCENT = LeoUi.CYAN;
    private static final int GOOD = Color.rgb(96, 211, 148);
    private static final int BAD = Color.rgb(255, 112, 112);
    private static final int WARN = Color.rgb(255, 193, 92);

    private LinearLayout profilesContainer;
    private TextView activationStatus;
    private TextView ramInfo;
    private EditText intervalInput;
    private TextView ramDetail,scheduleSummary;
    private android.widget.Spinner intervalUnit;

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
        boolean ram="ram".equals(getIntent().getStringExtra("screen"));
        LinearLayout root=LeoUi.page(this,ram?"Memória RAM":"Meus jogos",ram?"Espaço para a próxima partida.":"Um perfil para cada partida.",ram?-1:1,ram);
        activationStatus=text("",12,MUTED,true);activationStatus.setMinHeight(dp(48));activationStatus.setGravity(Gravity.CENTER_VERTICAL);
        activationStatus.setFocusable(true);activationStatus.setOnClickListener(v->startActivity(new Intent(this,ActivationActivity.class)));root.addView(activationStatus);
        if(ram){LinearLayout memory=card();memory.addView(text("MEMÓRIA LIVRE",11,MUTED,true));ramInfo=text("—",34,TEXT,true);memory.addView(ramInfo);
            ramDetail=text("",12,MUTED,false);memory.addView(ramDetail);memory.addView(spacer(18));memory.addView(LeoUi.button(this,"Limpar agora",true,v->cleanRamNow()));root.addView(memory);root.addView(spacer(16));
            LinearLayout schedule=card();schedule.addView(text("Limpeza automática",17,TEXT,true));scheduleSummary=text("",13,MUTED,false);scheduleSummary.setPadding(0,dp(6),0,dp(16));schedule.addView(scheduleSummary);
            schedule.addView(button("Alterar agendamento",v->editSchedule()));root.addView(schedule);
        }else{root.addView(LeoUi.button(this,"+  Adicionar jogo ou app",true,v->chooseApplication()));root.addView(spacer(18));profilesContainer=LeoUi.column(this);root.addView(profilesContainer,matchWrap());}
    }
    private void editSchedule(){
        LinearLayout form=card();long sec=getSharedPreferences(MonitorService.PREFS,0).getLong(MonitorService.KEY_INTERVAL_SEC,0);
        int unit=sec>0&&sec%3600==0?2:sec>0&&sec%60==0?1:0;long divisor=unit==2?3600:unit==1?60:1;
        intervalInput=editText(String.valueOf(sec==0?60:sec/divisor));intervalInput.setInputType(InputType.TYPE_CLASS_NUMBER);intervalInput.setContentDescription("Intervalo da limpeza");form.addView(label("Limpar a cada"));form.addView(intervalInput);
        intervalUnit=new android.widget.Spinner(this);intervalUnit.setAdapter(new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Segundos","Minutos","Horas"}));intervalUnit.setSelection(unit);form.addView(intervalUnit,new LinearLayout.LayoutParams(-1,dp(52)));
        form.addView(text("Intervalo mínimo: 10 segundos.",12,MUTED,false));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Agendamento").setView(form).setPositiveButton("Salvar",null).setNeutralButton("Desativar",(a,b)->stopOptimizer()).setNegativeButton("Cancelar",null).create();
        d.setOnShowListener(a->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(saveIntervalAndStart())d.dismiss();}));d.show();
    }

    private void refreshAll(){refreshActivationStatus();refreshMemory();renderProfiles();}

    private void refreshActivationStatus(){boolean ready=ShizukuCore.isOperational();activationStatus.setText(ready?"●  Conectado":"○  Toque para ativar a conexão");activationStatus.setTextColor(ready?GOOD:WARN);}

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

    private boolean saveIntervalAndStart(){
        try{long unit=intervalUnit.getSelectedItemPosition()==2?3600:intervalUnit.getSelectedItemPosition()==1?60:1;
            long sec=Math.multiplyExact(Long.parseLong(intervalInput.getText().toString().trim()),unit);
            if(sec<10||sec>Long.MAX_VALUE/1000){intervalInput.setError("Use pelo menos 10 segundos");return false;}
            if(!requireShizuku())return false;
            getSharedPreferences(MonitorService.PREFS,0).edit().putLong(MonitorService.KEY_INTERVAL_SEC,sec).apply();
            Intent i=new Intent(this,MonitorService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
            refreshMemory();Toast.makeText(this,"Agendamento salvo",0).show();return true;
        }catch(NumberFormatException|ArithmeticException e){intervalInput.setError("Digite um intervalo válido");return false;}
    }

    private void stopOptimizer() {
        getSharedPreferences(MonitorService.PREFS, MODE_PRIVATE).edit()
                .putLong(MonitorService.KEY_INTERVAL_SEC, 0L).apply();
        refreshMemory();
        ensureProfileMonitor();
        Toast.makeText(this, "Limpeza automática desativada", Toast.LENGTH_SHORT).show();
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

    private void refreshMemory(){
        if(ramInfo==null)return;SharedPreferences p=getSharedPreferences(MonitorService.PREFS,0);
        long last=p.getLong(MonitorService.KEY_LAST_CLEANUP,0),freed=p.getLong(MonitorService.KEY_LAST_FREED_MB,0),sec=p.getLong(MonitorService.KEY_INTERVAL_SEC,0);
        ramInfo.setText(String.format(java.util.Locale.forLanguageTag("pt-BR"),"%.1f GB",availableMemoryMb()/1024.0));
        ramDetail.setText(last==0?"Nenhuma limpeza realizada":p.getString(MonitorService.KEY_LAST_CLEANUP_RESULT,"").startsWith("ERRO")?"Última limpeza não concluída":"Última limpeza às "+android.text.format.DateFormat.format("HH:mm",last)+" · +"+freed+" MB");
        scheduleSummary.setText(sec==0?"Desativada":"A cada "+(sec%3600==0?sec/3600+" h":sec%60==0?sec/60+" min":sec+" s"));
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
                labels[i] = pm.getApplicationLabel(info).toString();
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

        form.addView(enabled);form.addView(label("DPI do perfil"));form.addView(density);form.addView(preview);form.addView(fastTouch);form.addView(linearDrag);
        LinearLayout advanced=LeoUi.column(this);advanced.setVisibility(View.GONE);
        form.addView(spacer(12));form.addView(button("Mais ajustes  +",v->{boolean open=advanced.getVisibility()==View.VISIBLE;advanced.setVisibility(open?View.GONE:View.VISIBLE);((Button)v).setText(open?"Mais ajustes  +":"Menos ajustes  −");}));
        advanced.addView(label("Largura-base (400 DPI)"));advanced.addView(width);advanced.addView(label("Altura-base (400 DPI)"));advanced.addView(height);advanced.addView(label("Intensidade do toque (1–100)"));advanced.addView(touchLevel);form.addView(advanced);form.addView(spacer(8));form.addView(text("Os recursos de toque dependem do Android e do jogo.",12,MUTED,false));

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

        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog editor=new AlertDialog.Builder(this).setTitle(LeoUi.appName(this,packageName)).setView(scroll).setPositiveButton("Salvar",null).setNegativeButton("Cancelar",null).create();
        editor.setOnShowListener(ignored->editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
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
                        applyAndSaveProfile(profile);editor.dismiss();
                    } catch (Exception e) {
                        Toast.makeText(this, "Use resolução 320–7680, DPI "
                                + PerAppCompat.MIN_DEDICATED_DPI + "–" + PerAppCompat.MAX_DEDICATED_DPI
                                + " e Touch 1–100", Toast.LENGTH_LONG).show();
                    }
                }));editor.show();
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
            output.setText("Tela estimada: "+plan.estimatedWidth+" × "+plan.estimatedHeight+adjusted);
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
                    Toast.makeText(this, "Perfil salvo. Abra o jogo para usar os ajustes.", Toast.LENGTH_LONG).show();
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

    private void renderProfiles(){
        if(profilesContainer==null)return;profilesContainer.removeAllViews();List<ProfileStore.Profile> profiles=ProfileStore.all(this);
        if(profiles.isEmpty()){LinearLayout empty=card();empty.addView(new LeoUi.Glyph(this,"game",ACCENT),new LinearLayout.LayoutParams(dp(64),dp(64)));
            empty.addView(text("Sua próxima partida começa aqui",17,TEXT,true));empty.addView(spacer(8));empty.addView(text("Adicione um jogo para personalizar tela e toque.",13,MUTED,false));profilesContainer.addView(empty);return;}
        for(ProfileStore.Profile profile:profiles){String name=LeoUi.appName(this,profile.packageName);LinearLayout box=card(),heading=LeoUi.row(this);
            android.widget.ImageView icon=new android.widget.ImageView(this);try{icon.setImageDrawable(getPackageManager().getApplicationIcon(profile.packageName));}catch(Exception ignored){}
            heading.addView(icon,new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout labels=LeoUi.column(this);labels.setPadding(dp(12),0,0,0);labels.addView(text(name,17,TEXT,true));labels.addView(text(profile.density+" DPI · "+(profile.enabled?"Perfil habilitado":"Pausado"),12,MUTED,false));heading.addView(labels,new LinearLayout.LayoutParams(0,-2,1));box.addView(heading);box.addView(spacer(16));
            LinearLayout actions=LeoUi.row(this);actions.addView(LeoUi.button(this,"Jogar",true,v->{Intent i=getPackageManager().getLaunchIntentForPackage(profile.packageName);if(i!=null)startActivity(i);else Toast.makeText(this,"Aplicativo não encontrado",0).show();}),new LinearLayout.LayoutParams(0,-2,1));actions.addView(spacerHorizontal(10));
            actions.addView(button("Ajustar",v->new AlertDialog.Builder(this).setTitle(name).setItems(new String[]{"Editar perfil","Frame Repeat","Reaplicar perfil","Remover perfil"},(d,n)->{
                if(n==0)editProfile(profile.packageName);else if(n==1)startActivity(new Intent(this,FrameRepeatActivity.class).putExtra("package",profile.packageName));else if(n==2)reapplyProfile(profile);
                else new AlertDialog.Builder(this).setTitle("Remover perfil?").setMessage("Os ajustes de "+name+" serão restaurados.").setPositiveButton("Remover",(x,w)->removeProfile(profile)).setNegativeButton("Cancelar",null).show();
            }).setNegativeButton("Fechar",null).show()),new LinearLayout.LayoutParams(0,-2,1));box.addView(actions);profilesContainer.addView(box);profilesContainer.addView(spacer(12));}
    }

    private CheckBox check(String label, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(TEXT);
        box.setChecked(checked);box.setMinHeight(dp(48));box.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
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

    private LinearLayout card(){return LeoUi.card(this);}

    private Button button(String label,View.OnClickListener listener){return LeoUi.button(this,label,false,listener);}

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
