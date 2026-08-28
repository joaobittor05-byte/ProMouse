package com.promouse;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private LinearLayout gamesList;
    private TextView statusValue;
    private TextView methodValue;
    private TextView mapperValue;

    private final int bg = Color.rgb(8, 12, 19);
    private final int card = Color.rgb(18, 25, 36);
    private final int border = Color.rgb(42, 54, 72);
    private final int blue = Color.rgb(72, 160, 255);
    private final int muted = Color.rgb(145, 157, 177);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        renderGames();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(16));
        root.setBackgroundColor(bg);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("ProMouse", 29, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1f));
        Button menu = button("☰", false);
        LinearLayout.LayoutParams menuLp = new LinearLayout.LayoutParams(dp(50), dp(48));
        header.addView(menu, menuLp);
        menu.setOnClickListener(v -> showMenu(menu));
        root.addView(header);

        TextView subtitle = text("Mouse + teclado → Touch", 12, muted, false);
        root.addView(subtitle, marginTop(0));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setPadding(0, dp(13), 0, 0);
        statusValue = stat(stats, "STATUS");
        methodValue = stat(stats, "MÉTODO");
        mapperValue = stat(stats, "MAPPER");
        root.addView(stats);

        LinearLayout gamesHeader = new LinearLayout(this);
        gamesHeader.setGravity(Gravity.CENTER_VERTICAL);
        gamesHeader.setPadding(0, dp(22), 0, dp(10));
        LinearLayout gameText = new LinearLayout(this);
        gameText.setOrientation(LinearLayout.VERTICAL);
        gameText.addView(text("Jogos", 20, Color.WHITE, true));
        gameText.addView(text("Adicione qualquer jogo instalado", 12, muted, false));
        gamesHeader.addView(gameText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button add = button("+", true);
        gamesHeader.addView(add, new LinearLayout.LayoutParams(dp(52), dp(52)));
        add.setOnClickListener(v -> showAppPicker());
        root.addView(gamesHeader);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        gamesList = new LinearLayout(this);
        gamesList.setOrientation(LinearLayout.VERTICAL);
        gamesList.setPadding(dp(10), dp(10), dp(10), dp(10));
        gamesList.setBackground(round(card, 18, border));
        scroll.addView(gamesList, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        return root;
    }

    private TextView stat(LinearLayout parent, String label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(8), dp(10), dp(8));
        box.setBackground(round(card, 12, border));
        TextView l = text(label, 9, muted, false);
        TextView value = text("—", 12, Color.WHITE, true);
        box.addView(l);
        box.addView(value, marginTop(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(58), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        parent.addView(box, lp);
        return value;
    }

    private void refreshStatus() {
        if (statusValue == null) return;
        boolean active = ActivationStore.isActive(this);
        statusValue.setText(active ? "Ativo" : "Desativado");
        methodValue.setText(ActivationStore.method(this));
        mapperValue.setText(active ? "Liberado" : "Bloqueado");
    }

    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Ativação");
        popup.getMenu().add("Observações");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getTitle().toString().startsWith("Ativação")) {
                startActivity(new Intent(this, ActivationActivity.class));
            } else {
                startActivity(new Intent(this, NotesActivity.class));
            }
            return true;
        });
        popup.show();
    }

    private void renderGames() {
        if (gamesList == null) return;
        gamesList.removeAllViews();
        List<String> packages = GameStore.list(this);
        if (packages.isEmpty()) {
            TextView empty = text("Nenhum jogo adicionado.\nUse + para escolher um aplicativo.", 14, muted, false);
            empty.setGravity(Gravity.CENTER);
            gamesList.addView(empty, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(190)));
            return;
        }

        PackageManager pm = getPackageManager();
        for (String pkg : packages) {
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) continue;
            String name = pkg;
            try { name = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); } catch (Exception ignored) {}
            gamesList.addView(gameRow(name, pkg));
        }
    }

    private View gameRow(String name, String pkg) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(8), dp(8));
        row.setBackground(round(Color.rgb(13, 19, 28), 13, border));

        ImageView icon = new ImageView(this);
        try { icon.setImageDrawable(getPackageManager().getApplicationIcon(pkg)); } catch (Exception ignored) {}
        row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView label = text(name, 15, Color.WHITE, true);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        labelLp.setMargins(dp(12), 0, dp(8), 0);
        label.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label, labelLp);

        Button open = button("ABRIR", false);
        row.addView(open, new LinearLayout.LayoutParams(dp(82), dp(42)));
        open.setOnClickListener(v -> openGame(pkg));
        row.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Remover jogo")
                    .setMessage("Remover " + name + " da lista do ProMouse?")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Remover", (d, w) -> { GameStore.remove(this, pkg); renderGames(); })
                    .show();
            return true;
        });

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(0, 0, 0, dp(8));
        wrapper.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)));
        return wrapper;
    }

    private void openGame(String pkg) {
        if (!ActivationStore.isActive(this)) {
            Toast.makeText(this, "Ative o ProMouse antes de iniciar o jogo.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, ActivationActivity.class));
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(permission);
            Toast.makeText(this, "Autorize o pop-up e toque em ABRIR novamente.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent service = new Intent(this, OverlayService.class);
        startForegroundService(service);
        Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch != null) startActivity(launch);
    }

    private void showAppPicker() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN, null);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = new ArrayList<>(pm.queryIntentActivities(query, 0));
        Collections.sort(apps, Comparator.comparing(a -> a.loadLabel(pm).toString().toLowerCase()));

        List<ResolveInfo> filtered = new ArrayList<>();
        for (ResolveInfo info : apps) {
            if (!info.activityInfo.packageName.equals(getPackageName())) filtered.add(info);
        }
        String[] labels = new String[filtered.size()];
        for (int i = 0; i < filtered.size(); i++) labels[i] = filtered.get(i).loadLabel(pm).toString();

        new AlertDialog.Builder(this)
                .setTitle("Adicionar jogo")
                .setItems(labels, (dialog, which) -> {
                    GameStore.add(this, filtered.get(which).activityInfo.packageName);
                    renderGames();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private Button button(String label, boolean accent) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(label.equals("+") ? 24 : 12);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(round(accent ? blue : card, 14, accent ? blue : border));
        return b;
    }

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private LinearLayout.LayoutParams marginTop(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = this.dp(dp);
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
