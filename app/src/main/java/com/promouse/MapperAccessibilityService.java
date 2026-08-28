package com.promouse;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class MapperAccessibilityService extends AccessibilityService {
    private static volatile MapperAccessibilityService instance;

    public static MapperAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Não lemos conteúdo da tela; o serviço é usado para gestos e teclas.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) {
            return false;
        }

        DisplayMetrics dm = getScreenMetrics();
        float w = dm.widthPixels;
        float h = dm.heightPixels;
        float joyX = w * 0.18f;
        float joyY = h * 0.72f;
        float dx = w * 0.10f;
        float dy = h * 0.14f;

        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_W:
                return swipe(joyX, joyY, joyX, joyY - dy, 130);
            case KeyEvent.KEYCODE_S:
                return swipe(joyX, joyY, joyX, joyY + dy, 130);
            case KeyEvent.KEYCODE_A:
                return swipe(joyX, joyY, joyX - dx, joyY, 130);
            case KeyEvent.KEYCODE_D:
                return swipe(joyX, joyY, joyX + dx, joyY, 130);
            case KeyEvent.KEYCODE_SPACE:
                return tap(w * 0.82f, h * 0.40f);
            case KeyEvent.KEYCODE_CTRL_LEFT:
            case KeyEvent.KEYCODE_CTRL_RIGHT:
                return tap(w * 0.76f, h * 0.65f);
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
                return tap(w * 0.29f, h * 0.66f);
            case KeyEvent.KEYCODE_F:
                return tap(w * 0.86f, h * 0.58f);
            case KeyEvent.KEYCODE_R:
                return tap(w * 0.91f, h * 0.44f);
            default:
                return false;
        }
    }

    public boolean tap(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 45);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();
        return dispatchGesture(gesture, null, null);
    }

    public boolean swipe(float x1, float y1, float x2, float y2, long durationMs) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, Math.max(60, durationMs));
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();
        return dispatchGesture(gesture, null, null);
    }

    private DisplayMetrics getScreenMetrics() {
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm != null) {
            wm.getDefaultDisplay().getRealMetrics(dm);
        }
        return dm;
    }
}
