package com.leo.optimazer;

import android.content.Context;
import android.content.SharedPreferences;

/** Configuração independente do Frame Repeat por aplicativo. */
final class FrameRepeatPrefs {
    static final String MODE_COMPETITIVE = "COMPETITIVE";
    static final String MODE_QUALITY = "QUALITY";
    static final String MODE_SMOOTH = "SMOOTH";

    private static final String PREFS = "leo_frame_repeat";
    private static final String ENABLED = "enabled:";
    private static final String MODE = "mode:";

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

    static String normalizeMode(String value) {
        if (MODE_QUALITY.equals(value)) return MODE_QUALITY;
        if (MODE_SMOOTH.equals(value)) return MODE_SMOOTH;
        return MODE_COMPETITIVE;
    }

    static String friendlyName(String mode) {
        String normalized = normalizeMode(mode);
        if (MODE_QUALITY.equals(normalized)) return "Qualidade";
        if (MODE_SMOOTH.equals(normalized)) return "Suave";
        return "Competitivo";
    }
}
