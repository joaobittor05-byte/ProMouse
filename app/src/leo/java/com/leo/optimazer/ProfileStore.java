package com.leo.optimazer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ProfileStore {
    private static final String PREFS = "leo_profiles";
    private static final String PREFIX = "profile:";
    private static final String KEY_DPI_MODEL_V11 = "dpi_model_v11";

    public static final class Profile {
        public final String packageName;
        /** Resolução-base usada quando a DPI dedicada está em 400. */
        public final int width;
        public final int height;
        /** DPI Android dedicada do aplicativo. 400 = padrão de referência do Leo. */
        public final int density;
        public final boolean restoreOnExit;
        public final boolean enabled;
        /** Prioriza resposta do toque e modo desempenho enquanto o app está em primeiro plano. */
        public final boolean fastTouch;
        /** Pede ao subsistema touch do fabricante maior estabilidade/suavidade de arrasto. */
        public final boolean linearDrag;
        /** Intensidade 1–100 do Touch Engine. */
        public final int touchLevel;

        public Profile(String packageName, int width, int height, int density,
                       boolean restoreOnExit, boolean enabled) {
            this(packageName, width, height, density, restoreOnExit, enabled, true, true, 85);
        }

        public Profile(String packageName, int width, int height, int density,
                       boolean restoreOnExit, boolean enabled,
                       boolean fastTouch, boolean linearDrag, int touchLevel) {
            this.packageName = packageName;
            this.width = width;
            this.height = height;
            this.density = density;
            this.restoreOnExit = restoreOnExit;
            this.enabled = enabled;
            this.fastTouch = fastTouch;
            this.linearDrag = linearDrag;
            this.touchLevel = Math.max(1, Math.min(100, touchLevel));
        }
    }

    private ProfileStore() {}

    private static SharedPreferences prefs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        migrateDpiModelIfNeeded(prefs);
        return prefs;
    }

    private static void migrateDpiModelIfNeeded(SharedPreferences prefs) {
        if (prefs.getBoolean(KEY_DPI_MODEL_V11, false)) return;

        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!entry.getKey().startsWith(PREFIX) || !(entry.getValue() instanceof String)) continue;
            String raw = (String) entry.getValue();
            try {
                String[] p = raw.split(",", -1);
                if (p.length < 5) continue;
                int oldDpi = Integer.parseInt(p[2]);
                int migrated = oldDpi >= 600 ? Math.round(oldDpi / 2f) : oldDpi;
                migrated = Math.max(PerAppCompat.MIN_DEDICATED_DPI,
                        Math.min(PerAppCompat.MAX_DEDICATED_DPI, migrated));

                String value = p[0] + "," + p[1] + "," + migrated + "," + p[3] + "," + p[4];
                if (p.length >= 8) {
                    value += "," + p[5] + "," + p[6] + "," + p[7];
                }
                editor.putString(entry.getKey(), value);
            } catch (Exception ignored) {}
        }
        editor.putBoolean(KEY_DPI_MODEL_V11, true).apply();
    }

    public static void save(Context context, Profile profile) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        PerAppCompat.DpiLimits limits = PerAppCompat.limitsForResolution(profile.width, profile.height, metrics);
        int normalizedDpi = limits.clamp(profile.density);

        String value = profile.width + "," + profile.height + "," + normalizedDpi + "," +
                profile.restoreOnExit + "," + profile.enabled + "," +
                profile.fastTouch + "," + profile.linearDrag + "," + profile.touchLevel;
        prefs(context).edit().putString(PREFIX + profile.packageName, value).apply();

        if (profile.enabled) ensureMonitor(context);
    }

    public static Profile get(Context context, String packageName) {
        String raw = prefs(context).getString(PREFIX + packageName, null);
        return raw == null ? null : decode(packageName, raw);
    }

    public static void delete(Context context, String packageName) {
        prefs(context).edit().remove(PREFIX + packageName).apply();
    }

    public static List<Profile> all(Context context) {
        List<Profile> result = new ArrayList<>();
        for (Map.Entry<String, ?> e : prefs(context).getAll().entrySet()) {
            if (!e.getKey().startsWith(PREFIX) || !(e.getValue() instanceof String)) continue;
            String pkg = e.getKey().substring(PREFIX.length());
            Profile profile = decode(pkg, (String) e.getValue());
            if (profile != null) result.add(profile);
        }
        Collections.sort(result, (a, b) -> a.packageName.compareToIgnoreCase(b.packageName));
        return result;
    }

    private static void ensureMonitor(Context context) {
        try {
            Intent intent = new Intent(context, MonitorService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (Throwable ignored) {}
    }

    private static Profile decode(String packageName, String raw) {
        try {
            String[] p = raw.split(",", -1);
            if (p.length != 5 && p.length != 8) return null;
            boolean fast = p.length >= 8 ? Boolean.parseBoolean(p[5]) : true;
            boolean linear = p.length >= 8 ? Boolean.parseBoolean(p[6]) : true;
            int level = p.length >= 8 ? Integer.parseInt(p[7]) : 85;
            return new Profile(
                    packageName,
                    Integer.parseInt(p[0]),
                    Integer.parseInt(p[1]),
                    Integer.parseInt(p[2]),
                    Boolean.parseBoolean(p[3]),
                    Boolean.parseBoolean(p[4]),
                    fast,
                    linear,
                    level
            );
        } catch (Exception ignored) {
            return null;
        }
    }
}
