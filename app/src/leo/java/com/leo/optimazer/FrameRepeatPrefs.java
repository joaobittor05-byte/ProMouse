package com.leo.optimazer;

import android.content.Context;
import android.content.SharedPreferences;

/** Configuração independente do Frame Repeat por aplicativo. */
final class FrameRepeatPrefs {
    static final String MODE_COMPETITIVE = "COMPETITIVE";
    static final String MODE_QUALITY = "QUALITY";
    static final String MODE_SMOOTH = "SMOOTH";

    // Controle do sincronismo do pipeline do Leo. Não desliga o VSync global do Android.
    static final String VSYNC_AUTO = "AUTO";
    static final String VSYNC_ON = "ON";
    static final String VSYNC_OFF = "OFF";

    private static final String PREFS = "leo_frame_repeat";
    private static final String ENABLED = "enabled:";
    private static final String MODE = "mode:";
    private static final String VSYNC = "vsync:";

    private FrameRepeatPrefs() {}

    static boolean isEnabled(Context context, String packageName, boolean legacyDefault) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.contains(ENABLED + packageName)) return legacyDefault;
        return p.getBoolean(ENABLED + packageName, legacyDefault);
    }

    static void setEnabled(Context context, String packageName, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(ENABLED + packageName, enabled).apply();
    }

    static String mode(Context context, String packageName) {
        String value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(MODE + packageName, MODE_COMPETITIVE);
        return normalizeMode(value);
    }

    static void setMode(Context context, String packageName, String mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(MODE + packageName, normalizeMode(mode)).apply();
    }

    static String vsync(Context context, String packageName) {
        String value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(VSYNC + packageName, VSYNC_AUTO);
        return normalizeVsync(value);
    }

    static void setVsync(Context context, String packageName, String value) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(VSYNC + packageName, normalizeVsync(value)).apply();
    }

    static String normalizeMode(String value) {
        if (MODE_QUALITY.equals(value)) return MODE_QUALITY;
        if (MODE_SMOOTH.equals(value)) return MODE_SMOOTH;
        return MODE_COMPETITIVE;
    }

    static String normalizeVsync(String value) {
        if (VSYNC_ON.equals(value)) return VSYNC_ON;
        if (VSYNC_OFF.equals(value)) return VSYNC_OFF;
        return VSYNC_AUTO;
    }

    static String friendlyName(String mode) {
        String normalized = normalizeMode(mode);
        if (MODE_QUALITY.equals(normalized)) return "Qualidade";
        if (MODE_SMOOTH.equals(normalized)) return "Suave";
        return "Competitivo";
    }

    static String friendlyVsync(String value) {
        String normalized = normalizeVsync(value);
        if (VSYNC_ON.equals(normalized)) return "Ligado";
        if (VSYNC_OFF.equals(normalized)) return "Desligado / Unlocked";
        return "Automático";
    }
}
