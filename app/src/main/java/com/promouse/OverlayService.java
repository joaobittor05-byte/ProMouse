package com.promouse;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.muntashirakon.adb.AdbStream;

public class OverlayService extends Service {
    public static final String EXTRA_TARGET_PACKAGE = "target_package";

    private static final String PREF_EDITOR = "promouse_editor";
    private static final long FOREGROUND_CHECK_MS = 1500L;
    private static final long LAUNCH_GRACE_MS = 6000L;

    private WindowManager wm;
    private LinearLayout bubble;
    private LinearLayout panel;
    private WindowManager.LayoutParams bubbleParams;
    private String targetPackage;
    private boolean gameForeground;
    private boolean monitorStarted;
    private long targetLaunchAt;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService monitorExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean checkingForeground = new AtomicBoolean(false);
    private final AtomicBoolean grantingUsageAccess = new AtomicBoolean(false);

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
                    boolean usageReady = hasUsageAccess();
                    if (!usageReady) {
                        tryGrantUsageAccessViaAdb();
                        usageReady = hasUsageAccess();
                    }
                    String foreground = usageReady ? detectForegroundPackage() : null;
                    final boolean reliable = usageReady;
                    checkingForeground.set(false);

                    mainHandler.post(() -> {
                        if (reliable && foreground != null && !foreground.isEmpty()) {
                            applyForegroundPackage(foreground);
                        } else if (!reliable) {
                            // Never interfere with the game when foreground detection is unavailable.
                            // During launch, keep the mapper visible; once Usage Access becomes available,
                            // normal show/hide behavior resumes automatically.
                            if (!gameForeground && SystemClock.elapsedRealtime() - targetLaunchAt >= 1200L) {
                                gameForeground = true;
                                showBubble();
                                updateNotification("Mapeamento ativo — preparando detecção do jogo");
                            }
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
                targetLaunchAt = SystemClock.elapsedRealtime();
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
            targetLaunchAt = SystemClock.elapsedRealtime();
        }

        startForegroundMonitor();
        return START_STICKY;
    }

    private void startForegroundMonitor() {
        if (monitorStarted) return;
        monitorStarted = true;
        mainHandler.postDelayed(foregroundMonitor, 800L);
    }

    private boolean hasUsageAccess() {
        try {
            AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void tryGrantUsageAccessViaAdb() {
        if (!"ADB Wi-Fi".equals(ActivationStore.method(this))) return;
        if (!grantingUsageAccess.compareAndSet(false, true)) return;
        AdbStream stream = null;
        try {
            ProMouseAdbManager manager = ProMouseAdbManager.getInstance(this);
            if (!manager.isConnected()) return;
            stream = manager.openStream("shell:appops set " + getPackageName() + " GET_USAGE_STATS allow");
        } catch (Throwable ignored) {
        } finally {
            try { if (stream != null) stream.close(); } catch (Throwable ignored) {}
            grantingUsageAccess.set(false);
        }
    }

    private String detectForegroundPackage() {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long end = System.currentTimeMillis();
            long begin = end - 10_000L;
            UsageEvents events = usm.queryEvents(begin, end);
            UsageEvents.Event event = new UsageEvents.Event();
            String latestPackage = null;
            long latestTime = -1L;

            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                int type = event.getEventType();
                if (type == UsageEvents.Event.MOVE_TO_FOREGROUND
                        || (Build.VERSION.SDK_INT >= 29 && type == UsageEvents.Event.ACTIVITY_RESUMED)) {
                    if (event.getTimeStamp() >= latestTime) {
                        latestTime = event.getTimeStamp();
                        latestPackage = event.getPackageName();
                    }
                }
            }
            return latestPackage;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void applyForegroundPackage(String foregroundPackage) {
        boolean targetIsForeground = targetPackage != null
                && targetPackage.equals(foregroundPackage)
                && ActivationStore.isActive(this);

        if (targetIsForeground) {
            if (!gameForeground) {
                gameForeground = true;
                showBubble();
            }
            updateNotification("Mapeamento ativo dentro do jogo");
            return;
        }

        // Give the game time to finish its launch transition. This branch only hides
        // ProMouse surfaces; it never stops, kills or changes the target application.
        if (SystemClock.elapsedRealtime() - targetLaunchAt < LAUNCH_GRACE_MS) return;

        if (gameForeground) {
            gameForeground = false;
            hideEditorSurfaces();
            updateNotification("Jogo em segundo plano — mapeamento pausado");
        }
    }

    private void showBubble() {
        if (!gameForeground || bubble != null || wm == null) return;

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

        try {
            wm.addView(bubble, bubbleParams);
        } catch (Throwable e) {
            bubble = null;
            bubbleParams = null;
            return;
        }

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
                try { if (bubble != null) wm.updateViewLayout(bubble, bubbleParams); }
                catch (Throwable ignored) {}
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
        bubbleParams.x = Math.max(0, Math.min(bubbleParams.x, Math.max(0, dm.widthPixels - dp(54))));
        bubbleParams.y = Math.max(0, Math.min(bubbleParams.y, Math.max(0, dm.heightPixels - dp(54))));
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
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(22)));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.addView(tool("FPS"));
        tools.addView(tool("TOQUE"));
        tools.addView(tool("ANALÓGICO"));
        tools.addView(tool("⚙"));
        panel.addView(tools, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button save = actionButton("SALVAR", true);
        Button exit = actionButton("SAIR", false);
        actions.addView(save);
        actions.addView(exit);
        panel.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

        save.setOnClickListener(v -> saveAndCloseEditor());
        exit.setOnClickListener(v -> closePanel());

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int available = Math.max(dp(260), dm.widthPixels - dp(16));
        int panelWidth = Math.min(dp(388), available);
        int panelHeight = dp(128);
        WindowManager.LayoutParams p = overlayParams(panelWidth, panelHeight);
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = Math.max(dp(8), Math.min(bubbleParams.x,
                Math.max(dp(8), dm.widthPixels - panelWidth - dp(8))));
        int above = bubbleParams.y - panelHeight - dp(8);
        int below = bubbleParams.y + dp(62);
        p.y = above >= dp(8)
                ? above
                : Math.max(dp(8), Math.min(below, dm.heightPixels - panelHeight - dp(8)));
        try { wm.addView(panel, p); }
        catch (Throwable ignored) { panel = null; }
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
            try { wm.removeView(panel); } catch (Throwable ignored) {}
        }
        panel = null;
    }

    private void hideEditorSurfaces() {
        closePanel();
        if (wm != null && bubble != null) {
            try { wm.removeView(bubble); } catch (Throwable ignored) {}
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
