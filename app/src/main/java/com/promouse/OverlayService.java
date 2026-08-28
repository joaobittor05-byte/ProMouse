package com.promouse;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.muntashirakon.adb.AdbStream;

public class OverlayService extends Service {
    public static final String EXTRA_TARGET_PACKAGE = "target_package";

    private static final String PREF_EDITOR = "promouse_editor";
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?:^|\\s)([A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)/");
    private static final long FOREGROUND_CHECK_MS = 850L;

    private WindowManager wm;
    private LinearLayout bubble;
    private LinearLayout panel;
    private WindowManager.LayoutParams bubbleParams;
    private String targetPackage;
    private boolean gameForeground;
    private boolean monitorStarted;
    private int detectionFailures;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService monitorExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean checkingForeground = new AtomicBoolean(false);

    private final Runnable foregroundMonitor = new Runnable() {
        @Override
        public void run() {
            if (!ActivationStore.isActive(OverlayService.this)) {
                hideEditorSurfaces();
                stopSelf();
                return;
            }
            if (targetPackage == null || targetPackage.isEmpty()) {
                hideEditorSurfaces();
                stopSelf();
                return;
            }

            if (checkingForeground.compareAndSet(false, true)) {
                monitorExecutor.execute(() -> {
                    String foreground = detectForegroundPackage();
                    checkingForeground.set(false);
                    mainHandler.post(() -> {
                        if (foreground == null || foreground.isEmpty()) {
                            detectionFailures++;
                            if (detectionFailures >= 2 && gameForeground) {
                                gameForeground = false;
                                hideEditorSurfaces();
                                updateNotification("Não foi possível confirmar o jogo — mapeamento pausado");
                            }
                        } else {
                            detectionFailures = 0;
                            applyForegroundPackage(foreground);
                        }
                    });
                });
            }
            mainHandler.postDelayed(this, FOREGROUND_CHECK_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        startForegroundNow("Aguardando o jogo para ativar o mapeamento");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!ActivationStore.isActive(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null) {
            String requested = intent.getStringExtra(EXTRA_TARGET_PACKAGE);
            if (requested != null && !requested.trim().isEmpty()) {
                targetPackage = requested.trim();
                getSharedPreferences(PREF_EDITOR, MODE_PRIVATE).edit()
                        .putString("active_target", targetPackage)
                        .apply();
                gameForeground = false;
                hideEditorSurfaces();
            }
        }

        if (targetPackage == null || targetPackage.isEmpty()) {
            targetPackage = getSharedPreferences(PREF_EDITOR, MODE_PRIVATE)
                    .getString("active_target", "");
        }

        startForegroundMonitor();
        return START_STICKY;
    }

    private void startForegroundMonitor() {
        if (monitorStarted) return;
        monitorStarted = true;
        mainHandler.post(foregroundMonitor);
    }

    private String detectForegroundPackage() {
        if (!"ADB Wi-Fi".equals(ActivationStore.method(this))) return null;
        try {
            ProMouseAdbManager manager = ProMouseAdbManager.getInstance(this);
            if (!manager.isConnected()) return null;

            String output = runAdbCommand(manager,
                    "dumpsys activity activities | grep mResumedActivity | head -n 1");
            String pkg = parsePackage(output);
            if (pkg != null) return pkg;

            output = runAdbCommand(manager,
                    "dumpsys window windows | grep mCurrentFocus | head -n 1");
            return parsePackage(output);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String runAdbCommand(ProMouseAdbManager manager, String command) throws Exception {
        AdbStream stream = null;
        BufferedReader reader = null;
        try {
            stream = manager.openStream("shell:" + command);
            reader = new BufferedReader(new InputStreamReader(stream.openInputStream()));
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
                if (out.length() > 4096) break;
            }
            return out.toString();
        } finally {
            try { if (reader != null) reader.close(); } catch (Exception ignored) {}
            try { if (stream != null) stream.close(); } catch (Exception ignored) {}
        }
    }

    private String parsePackage(String text) {
        if (text == null) return null;
        Matcher matcher = PACKAGE_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private void applyForegroundPackage(String foregroundPackage) {
        boolean shouldBeActive = ActivationStore.isActive(this)
                && targetPackage != null
                && targetPackage.equals(foregroundPackage);

        if (shouldBeActive && !gameForeground) {
            gameForeground = true;
            showBubble();
            updateNotification("Mapeamento ativo dentro do jogo");
        } else if (!shouldBeActive && gameForeground) {
            gameForeground = false;
            hideEditorSurfaces();
            updateNotification("Jogo em segundo plano — mapeamento pausado");
        }
    }

    private void showBubble() {
        if (!gameForeground || bubble != null) return;

        bubble = new LinearLayout(this);
        bubble.setGravity(Gravity.CENTER);
        bubble.setElevation(dp(8));
        bubble.setBackground(round(Color.rgb(36, 134, 235), 28, Color.rgb(115, 197, 255)));

        TextView pm = new TextView(this);
        pm.setText("PM");
        pm.setTextColor(Color.WHITE);
        pm.setTextSize(13);
        pm.setGravity(Gravity.CENTER);
        pm.setTypeface(pm.getTypeface(), android.graphics.Typeface.BOLD);
        bubble.addView(pm, new LinearLayout.LayoutParams(dp(54), dp(54)));

        bubbleParams = overlayParams(dp(54), dp(54));
        bubbleParams.gravity = Gravity.TOP | Gravity.START;

        SharedPreferences prefs = getSharedPreferences(PREF_EDITOR, MODE_PRIVATE);
        bubbleParams.x = prefs.getInt(positionKey("bubble_x"), dp(16));
        bubbleParams.y = prefs.getInt(positionKey("bubble_y"), dp(140));
        clampBubblePosition();
        wm.addView(bubble, bubbleParams);

        final float[] start = new float[4];
        bubble.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                start[0] = e.getRawX();
                start[1] = e.getRawY();
                start[2] = bubbleParams.x;
                start[3] = bubbleParams.y;
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_MOVE) {
                bubbleParams.x = (int) (start[2] + e.getRawX() - start[0]);
                bubbleParams.y = (int) (start[3] + e.getRawY() - start[1]);
                clampBubblePosition();
                if (bubble != null) wm.updateViewLayout(bubble, bubbleParams);
                if (panel != null) closePanel();
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP) {
                float dx = Math.abs(e.getRawX() - start[0]);
                float dy = Math.abs(e.getRawY() - start[1]);
                if (dx < dp(8) && dy < dp(8)) togglePanel();
                return true;
            }
            return false;
        });
    }

    private void clampBubblePosition() {
        if (bubbleParams == null) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        bubbleParams.x = Math.max(0, Math.min(bubbleParams.x, dm.widthPixels - dp(54)));
        bubbleParams.y = Math.max(0, Math.min(bubbleParams.y, dm.heightPixels - dp(54)));
    }

    private void togglePanel() {
        if (!gameForeground || bubble == null) return;
        if (panel != null) {
            closePanel();
            return;
        }

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(7), dp(8), dp(8));
        panel.setElevation(dp(10));
        panel.setBackground(round(Color.argb(248, 12, 18, 28), 18, Color.rgb(59, 80, 108)));

        TextView title = new TextView(this);
        title.setText("EDITAR MAPEAMENTO");
        title.setTextColor(Color.rgb(150, 168, 194));
        title.setTextSize(9);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(5), 0, 0, dp(4));
        panel.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(22)));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.addView(tool("FPS"));
        tools.addView(tool("TOQUE"));
        tools.addView(tool("ANALÓGICO"));
        tools.addView(tool("⚙"));
        panel.addView(tools, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button save = actionButton("SALVAR", true);
        Button exit = actionButton("SAIR", false);
        actions.addView(save);
        actions.addView(exit);
        panel.addView(actions, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

        save.setOnClickListener(v -> saveAndCloseEditor());
        exit.setOnClickListener(v -> closePanel());

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int panelWidth = Math.min(dp(388), Math.max(dp(280), dm.widthPixels - dp(16)));
        int panelHeight = dp(128);
        WindowManager.LayoutParams p = overlayParams(panelWidth, panelHeight);
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = Math.max(dp(8), Math.min(bubbleParams.x, dm.widthPixels - panelWidth - dp(8)));
        int above = bubbleParams.y - panelHeight - dp(8);
        int below = bubbleParams.y + dp(62);
        p.y = above >= dp(8) ? above : Math.min(below, dm.heightPixels - panelHeight - dp(8));
        wm.addView(panel, p);
    }

    private Button tool(String name) {
        Button b = new Button(this);
        b.setText(name);
        b.setAllCaps(false);
        b.setTextSize(name.equals("ANALÓGICO") ? 9 : 11);
        b.setTextColor(Color.WHITE);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(round(Color.rgb(24, 34, 49), 12, Color.rgb(56, 77, 104)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> Toast.makeText(this,
                name + " — editor do controle", Toast.LENGTH_SHORT).show());
        return b;
    }

    private Button actionButton(String name, boolean accent) {
        Button b = new Button(this);
        b.setText(name);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setTypeface(b.getTypeface(), android.graphics.Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(round(
                accent ? Color.rgb(45, 142, 242) : Color.rgb(21, 29, 42),
                11,
                accent ? Color.rgb(105, 190, 255) : Color.rgb(56, 75, 100)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        lp.setMargins(dp(3), dp(4), dp(3), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void saveAndCloseEditor() {
        if (bubbleParams != null && targetPackage != null) {
            getSharedPreferences(PREF_EDITOR, MODE_PRIVATE).edit()
                    .putInt(positionKey("bubble_x"), bubbleParams.x)
                    .putInt(positionKey("bubble_y"), bubbleParams.y)
                    .putLong(positionKey("last_saved"), System.currentTimeMillis())
                    .apply();
        }
        closePanel();
        Toast.makeText(this, "Mapeamento salvo", Toast.LENGTH_SHORT).show();
    }

    private String positionKey(String suffix) {
        String pkg = targetPackage == null ? "default" : targetPackage;
        return pkg + "_" + suffix;
    }

    private void closePanel() {
        if (wm != null && panel != null) {
            try { wm.removeView(panel); } catch (Exception ignored) {}
        }
        panel = null;
    }

    private void hideEditorSurfaces() {
        closePanel();
        if (wm != null && bubble != null) {
            try { wm.removeView(bubble); } catch (Exception ignored) {}
        }
        bubble = null;
        bubbleParams = null;
    }

    private WindowManager.LayoutParams overlayParams(int width, int height) {
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        return new WindowManager.LayoutParams(
                width,
                height,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
    }

    private void startForegroundNow(String text) {
        String id = "promouse_mapper";
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                    id, "ProMouse Mapper", NotificationManager.IMPORTANCE_LOW));
        }
        startForeground(3201, buildNotification(id, text));
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(3201, buildNotification("promouse_mapper", text));
    }

    private Notification buildNotification(String channelId, String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle("ProMouse")
                .setContentText(text)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        hideEditorSurfaces();
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(foregroundMonitor);
        monitorExecutor.shutdownNow();
        hideEditorSurfaces();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
