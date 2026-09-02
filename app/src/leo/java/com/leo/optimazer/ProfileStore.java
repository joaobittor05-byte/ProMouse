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

    public static final class Profile {
        public final String packageName;
        public final int width;
        public final int height;
        /** DPI Virtual do Leo (estilo mouse): 800 = padrão, 1600 = 2x, etc. */
        public final int density;
        public final boolean restoreOnExit;
        public final boolean enabled;

        public Profile(String packageName, int width, int height, int density, boolean restoreOnExit, boolean enabled) {
            this.packageName = packageName;
            this.width = width;
            this.height = height;
            this.density = density;
            this.restoreOnExit = restoreOnExit;
            this.enabled = enabled;
        }
    }

    private ProfileStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void save(Context context, Profile profile) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int requestedVirtualDpi = profile.density;

        // Migração simples das builds antigas: o antigo campo podia vir preenchido
        // exatamente com a densidade Android do aparelho. No modelo novo, o padrão é 800.
        if (requestedVirtualDpi == metrics.densityDpi) {
            requestedVirtualDpi = PerAppCompat.VIRTUAL_DPI_BASE;
        }

        PerAppCompat.DpiLimits limits = PerAppCompat.limitsForResolution(
                profile.width,
                profile.height,
                metrics
        );
        int normalizedVirtualDpi = limits.clamp(requestedVirtualDpi);

        String value = profile.width + "," + profile.height + "," + normalizedVirtualDpi + "," +
                profile.restoreOnExit + "," + profile.enabled;
        prefs(context).edit().putString(PREFIX + profile.packageName, value).apply();

        // A DPI é aplicada quando o app entra em primeiro plano. Portanto o monitor
        // precisa existir mesmo quando a limpeza automática de RAM estiver desligada.
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
            if (p.length != 5) return null;
            return new Profile(
                    packageName,
                    Integer.parseInt(p[0]),
                    Integer.parseInt(p[1]),
                    Integer.parseInt(p[2]),
                    Boolean.parseBoolean(p[3]),
                    Boolean.parseBoolean(p[4])
            );
        } catch (Exception ignored) {
            return null;
        }
    }
}
