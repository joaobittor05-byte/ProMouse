package com.promouse;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private LinearLayout gamesList;
    private TextView statusValue;
    private TextView methodValue;
    private TextView mapperValue;
    private TextView gamesCount;

    private final int bgTop = Color.rgb(7, 12, 20);
    private final int bgBottom = Color.rgb(10, 17, 28);
    private final int card = Color.rgb(18, 26, 39);
    private final int cardDeep = Color.rgb(13, 20, 31);
    private final int border = Color.rgb(43, 57, 78);
    private final int blue = Color.rgb(70, 158, 255);
    private final int blueSoft = Color.rgb(115, 195, 255);
    private final int muted = Color.rgb(148, 162, 184);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        startCoreService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        renderGames();
    }

    private void startCoreService() {
        Intent core = new Intent(this, ProMouseCoreService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(core);
            else startService(core);
        } catch (Exception ignored) {}
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        root.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{bgTop, bgBottom}));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.addView(text("ProMouse", 30, Color.WHITE, true));

        LinearLayout brandLine = new LinearLayout(this);
        brandLine.setGravity(Gravity.CENTER_VERTICAL);
        brandLine.addView(text("Mouse + teclado → Touch", 12, muted, false));
        TextView badge = text("MAPPER", 8, blueSoft, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(round(Color.rgb(14, 34, 56), 9, Color.rgb(42, 92, 139)));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(58), dp(22));
        badgeLp.setMargins(dp(10), 0, 0, 0);
        brandLine.addView(badge, badgeLp);
        brand.addView(brandLine, marginTop(2));
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(72), 1f));

        Button menu = button("☰", false);
        header.addView(menu, new LinearLayout.LayoutParams(dp(52), dp(50)));
        menu.setOnClickListener(v -> showMenu(menu));
        root.addView(header);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setPadding(0, dp(10), 0, 0);
        statusValue = stat(stats, "STATUS");
        methodValue = stat(stats, "MÉTODO");
        mapperValue = stat(stats, "MAPPER");
        root.addView(stats);

        LinearLayout gamesHeader = new LinearLayout(this);
        gamesHeader.setGravity(Gravity.CENTER_VERTICAL);
        gamesHeader.setPadding(0, dp(20), 0, dp(10));

        LinearLayout gameText = new LinearLayout(this);
        gameText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout titleLine = new LinearLayout(this);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);
        titleLine.addView(text("Jogos", 21, Color.WHITE, true));
        gamesCount = text("0", 9, blueSoft, true);
        gamesCount.setGravity(Gravity.CENTER);
        gamesCount.setBackground(round(Color.rgb(15, 35, 57), 10, Color.rgb(43, 91, 139)));
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(dp(34), dp(22));
        countLp.setMargins(dp(8), 0, 0, 0);
        titleLine.addView(gamesCount, countLp);
        gameText.addView(titleLine);
        gameText.addView(text("Seus jogos e aplicativos mapeados", 12, muted, false), marginTop(2));
        gamesHeader.addView(gameText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button add = button("+", true);
        gamesHeader.addView(add, new LinearLayout.LayoutParams(dp(54), dp(54)));
        add.setOnClickListener(v -> showAppPicker());
        root.addView(gamesHeader);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        gamesList = new LinearLayout(this);
        gamesList.setOrientation(LinearLayout.VERTICAL);
        gamesList.setPadding(dp(10), dp(10), dp(10), dp(10));
        gamesList.setBackground(round(Color.rgb(11, 17, 27), 20, border));
        scroll.addView(gamesList, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        return root;
    }

    private TextView stat(LinearLayout parent, String label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(7), dp(10), dp(7));
        box.setBackground(round(card, 12, border));

        TextView l = text(label, 8, muted, false);
        TextView value = text("—", 11, Color.WHITE, true);
        box.addView(l);
        box.addView(value, marginTop(1));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(53), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        parent.addView(box, lp);
        return value;
    }

    private void refreshStatus() {
        if (statusValue == null) return;
        boolean active = ActivationStore.isActive(this);
        statusValue.setText(active ? "● Ativo" : "○ Off");
        statusValue.setTextColor(active ? Color.rgb(91, 220, 154) : Color.WHITE);
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
        if (gamesCount != null) gamesCount.setText(String.valueOf(packages.size()));

        if (packages.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            TextView icon = text("＋", 34, blueSoft, false);
            icon.setGravity(Gravity.CENTER);
            empty.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
            TextView title = text("Nenhum jogo adicionado", 14, Color.WHITE, true);
            title.setGravity(Gravity.CENTER);
            empty.addView(title);
            TextView hint = text("Toque em + para escolher um aplicativo", 12, muted, false);
            hint.setGravity(Gravity.CENTER);
            empty.addView(hint, marginTop(4));
            gamesList.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(220)));
            return;
        }

        PackageManager pm = getPackageManager();
        for (String pkg : packages) {
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) continue;
            String name = pkg;
            try { name = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); }
            catch (Exception ignored) {}
            gamesList.addView(gameRow(name, pkg));
        }
    }

    private View gameRow(String name, String pkg) {
        LinearLayout cardRow = new LinearLayout(this);
        cardRow.setGravity(Gravity.CENTER_VERTICAL);
        cardRow.setPadding(dp(10), dp(8), dp(8), dp(8));
        cardRow.setBackground(round(cardDeep, 15, border));

        LinearLayout iconPlate = new LinearLayout(this);
        iconPlate.setGravity(Gravity.CENTER);
        iconPlate.setBackground(round(Color.rgb(22, 31, 45), 13, Color.rgb(52, 68, 91)));
        ImageView icon = new ImageView(this);
        icon.setPadding(dp(5), dp(5), dp(5), dp(5));
        try { icon.setImageDrawable(getPackageManager().getApplicationIcon(pkg)); }
        catch (Exception ignored) {}
        iconPlate.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));
        cardRow.addView(iconPlate, new LinearLayout.LayoutParams(dp(50), dp(50)));

        TextView label = text(name, 15, Color.WHITE, true);
        label.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        labelLp.setMargins(dp(12), 0, dp(8), 0);
        cardRow.addView(label, labelLp);

        Button open = button("ABRIR", true);
        cardRow.addView(open, new LinearLayout.LayoutParams(dp(82), dp(42)));
        open.setOnClickListener(v -> openGame(pkg));
        cardRow.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Remover jogo")
                    .setMessage("Remover " + name + " da lista do ProMouse?")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Remover", (d, w) -> {
                        GameStore.remove(this, pkg);
                        renderGames();
                    })
                    .show();
            return true;
        });

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(0, 0, 0, dp(9));
        wrapper.addView(cardRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(70)));
        return wrapper;
    }

    private void openGame(String pkg) {
        if (!ActivationStore.isActive(this)) {
            Toast.makeText(this, "Ative o ProMouse antes de iniciar o jogo.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, ActivationActivity.class));
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Intent permission = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(permission);
            Toast.makeText(this,
                    "Autorize o pop-up e toque em ABRIR novamente.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Intent service = new Intent(this, OverlayService.class)
                .putExtra(OverlayService.EXTRA_TARGET_PACKAGE, pkg);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
        else startService(service);

        Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        }
    }

    private void showAppPicker() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN, null);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = new ArrayList<>(pm.queryIntentActivities(query, 0));
        Collections.sort(apps, Comparator.comparing(
                a -> a.loadLabel(pm).toString().toLowerCase()));

        Set<String> added = new HashSet<>(GameStore.list(this));
        AlertDialog dialog = new AlertDialog.Builder(this).create();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(16), dp(16), dp(12));
        panel.setBackground(round(Color.rgb(14, 21, 33), 20, border));

        TextView title = text("Adicionar aplicativo", 20, Color.WHITE, true);
        panel.addView(title);
        panel.addView(text("Escolha pelo nome ou pelo ícone", 12, muted, false), marginTop(3));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(12), 0, dp(8));

        for (ResolveInfo info : apps) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            String name = info.loadLabel(pm).toString();

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(9), dp(7), dp(9), dp(7));
            row.setBackground(round(Color.rgb(19, 28, 42), 13, Color.rgb(44, 59, 81)));

            ImageView appIcon = new ImageView(this);
            appIcon.setImageDrawable(info.loadIcon(pm));
            appIcon.setPadding(dp(3), dp(3), dp(3), dp(3));
            row.addView(appIcon, new LinearLayout.LayoutParams(dp(48), dp(48)));

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView appName = text(name, 14, Color.WHITE, true);
            texts.addView(appName);
            if (added.contains(pkg)) {
                TextView tag = text("Já adicionado", 10, Color.rgb(91, 220, 154), false);
                texts.addView(tag, marginTop(2));
            } else {
                TextView tag = text("Toque para adicionar", 10, muted, false);
                texts.addView(tag, marginTop(2));
            }
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, dp(52), 1f);
            textLp.setMargins(dp(11), 0, 0, 0);
            row.addView(texts, textLp);

            if (!added.contains(pkg)) {
                row.setOnClickListener(v -> {
                    GameStore.add(this, pkg);
                    dialog.dismiss();
                    renderGames();
                });
            }

            LinearLayout holder = new LinearLayout(this);
            holder.setPadding(0, 0, 0, dp(7));
            holder.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(66)));
            list.addView(holder);
        }

        scroll.addView(list);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        Button close = button("FECHAR", false);
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        closeLp.topMargin = dp(6);
        panel.addView(close, closeLp);

        dialog.setView(panel);
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dialog.getWindow().setLayout(
                        (int) (getResources().getDisplayMetrics().widthPixels * 0.92f),
                        (int) (getResources().getDisplayMetrics().heightPixels * 0.82f));
            }
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.92f),
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.82f));
        }
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
        b.setTextSize(label.equals("+") ? 24 : 11);
        b.setTypeface(b.getTypeface(), android.graphics.Typeface.BOLD);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(round(
                accent ? blue : card,
                14,
                accent ? blueSoft : border));
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = this.dp(dp);
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
