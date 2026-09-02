package com.leo.optimazer;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class PrivilegedOps {
    private final Context context;

    public PrivilegedOps(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean isActivated() {
        return hasWriteSecureSettings() && hasUsageAccess();
    }

    public boolean hasWriteSecureSettings() {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public boolean hasUsageAccess() {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public boolean supports(String command) {
        String op = command == null ? "" : command.trim().split("\\s+")[0].toUpperCase();
        return !"KILL_CACHED".equals(op);
    }

    public String send(String raw) throws Exception {
        if (!isActivated()) throw new SecurityException("Ativação Brevent incompleta");
        String[] parts = raw.trim().split("\\s+");
        String op = parts[0].toUpperCase();

        switch (op) {
            case "PING":
                return "pong";
            case "TOP":
                return topPackage();
            case "GET_SIZE":
                return exec("wm", "size");
            case "GET_DENSITY":
                return exec("wm", "density");
            case "SET_SIZE":
                if (parts.length != 3) throw new IllegalArgumentException("SET_SIZE width height");
                return exec("wm", "size", parts[1] + "x" + parts[2]);
            case "SET_DENSITY":
                if (parts.length != 2) throw new IllegalArgumentException("SET_DENSITY density");
                return exec("wm", "density", parts[1]);
            case "RESET_SIZE":
                return exec("wm", "size", "reset");
            case "RESET_DENSITY":
                return exec("wm", "density", "reset");
            case "KILL_CACHED":
                throw new UnsupportedOperationException("Limpeza global de RAM exige bridge shell ativo");
            default:
                throw new SecurityException("Operação não permitida: " + op);
        }
    }

    private String topPackage() {
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return "";

        long now = System.currentTimeMillis();
        long begin = now - 60_000L;
        UsageEvents events = usm.queryEvents(begin, now);
        UsageEvents.Event event = new UsageEvents.Event();
        String lastPackage = null;
        long lastTime = 0L;

        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int type = event.getEventType();
            boolean foreground = type == UsageEvents.Event.MOVE_TO_FOREGROUND;
            if (Build.VERSION.SDK_INT >= 29) {
                foreground = foreground || type == UsageEvents.Event.ACTIVITY_RESUMED;
            }
            if (foreground && event.getTimeStamp() >= lastTime) {
                lastTime = event.getTimeStamp();
                lastPackage = event.getPackageName();
            }
        }

        if (lastPackage != null && !lastPackage.isEmpty()) return lastPackage;

        List<UsageStats> stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 86_400_000L,
                now
        );
        if (stats == null) return "";

        UsageStats newest = null;
        for (UsageStats stat : stats) {
            if (newest == null || stat.getLastTimeUsed() > newest.getLastTimeUsed()) newest = stat;
        }
        return newest == null ? "" : newest.getPackageName();
    }

    private String exec(String... command) throws Exception {
        java.lang.Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = process.getInputStream()) {
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) >= 0) buffer.write(chunk, 0, n);
        }
        int code = process.waitFor();
        String out = buffer.toString(StandardCharsets.UTF_8.name()).trim();
        if (code != 0) throw new IllegalStateException(command[0] + " retornou " + code + ": " + out);
        return out;
    }

    public static String breventActivationCommand() {
        return "pm grant com.leo.optimazer android.permission.WRITE_SECURE_SETTINGS";
    }
}
