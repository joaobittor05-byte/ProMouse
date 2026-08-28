package com.promouse;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class OverlayService extends Service {
    private WindowManager wm;
    private LinearLayout bubble;
    private LinearLayout panel;
    private WindowManager.LayoutParams bubbleParams;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundNow();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        showBubble();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!ActivationStore.isActive(this)) stopSelf();
        return START_STICKY;
    }

    private void showBubble() {
        if (bubble != null) return;
        bubble = new LinearLayout(this);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackground(round(Color.rgb(37, 127, 225), 25, Color.rgb(107, 187, 255)));
        TextView pm = new TextView(this);
        pm.setText("PM"); pm.setTextColor(Color.WHITE); pm.setTextSize(13); pm.setGravity(Gravity.CENTER); pm.setTypeface(pm.getTypeface(), android.graphics.Typeface.BOLD);
        bubble.addView(pm, new LinearLayout.LayoutParams(dp(52), dp(52)));

        bubbleParams = overlayParams(dp(52), dp(52));
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = dp(16); bubbleParams.y = dp(140);
        wm.addView(bubble, bubbleParams);

        final float[] start = new float[4];
        bubble.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                start[0] = e.getRawX(); start[1] = e.getRawY(); start[2] = bubbleParams.x; start[3] = bubbleParams.y; return true;
            }
            if (e.getAction() == MotionEvent.ACTION_MOVE) {
                bubbleParams.x = (int) (start[2] + e.getRawX() - start[0]);
                bubbleParams.y = (int) (start[3] + e.getRawY() - start[1]);
                wm.updateViewLayout(bubble, bubbleParams); return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP) {
                float dx = Math.abs(e.getRawX() - start[0]); float dy = Math.abs(e.getRawY() - start[1]);
                if (dx < dp(8) && dy < dp(8)) togglePanel();
                return true;
            }
            return false;
        });
    }

    private void togglePanel() {
        if (panel != null) { wm.removeView(panel); panel = null; return; }
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setPadding(dp(6), dp(6), dp(6), dp(6));
        panel.setBackground(round(Color.argb(235, 12, 18, 27), 16, Color.rgb(63, 79, 102)));
        panel.addView(tool("FPS"));
        panel.addView(tool("TOQUE"));
        panel.addView(tool("ANALÓGICO"));
        panel.addView(tool("⚙"));
        WindowManager.LayoutParams p = overlayParams(dp(314), dp(58));
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = Math.max(0, bubbleParams.x);
        p.y = Math.max(0, bubbleParams.y - dp(66));
        wm.addView(panel, p);
    }

    private Button tool(String name) {
        Button b = new Button(this);
        b.setText(name); b.setAllCaps(false); b.setTextSize(name.equals("ANALÓGICO") ? 9 : 11); b.setTextColor(Color.WHITE);
        b.setBackground(round(Color.rgb(24, 34, 48), 11, Color.rgb(56, 75, 99)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f); lp.setMargins(dp(2), 0, dp(2), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> Toast.makeText(this, name + " — editor será conectado ao motor de mapeamento", Toast.LENGTH_SHORT).show());
        return b;
    }

    private WindowManager.LayoutParams overlayParams(int width, int height) {
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        return new WindowManager.LayoutParams(width, height, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
    }

    private void startForegroundNow() {
        String id = "promouse_mapper";
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(id, "ProMouse Mapper", NotificationManager.IMPORTANCE_LOW));
        android.app.Notification n = new android.app.Notification.Builder(this, id)
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle("ProMouse ativo")
                .setContentText("Pop-up do editor disponível sobre o jogo")
                .setOngoing(true)
                .build();
        startForeground(3201, n);
    }

    @Override
    public void onDestroy() {
        if (wm != null && panel != null) wm.removeView(panel);
        if (wm != null && bubble != null) wm.removeView(bubble);
        panel = null; bubble = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), stroke); return d;
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
