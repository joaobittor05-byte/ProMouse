package com.leo.optimazer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Touch Engine universal executado dentro do UserService do Shizuku.
 *
 * Não depende de Game Turbo, Xiaomi, Samsung Game Booster ou HAL de fabricante.
 * O controle de refresh foi movido para o Frame Match para evitar dois módulos alterando
 * min_refresh_rate/peak_refresh_rate ao mesmo tempo.
 */
final class TouchEngineController {
    private static String activePackage;

    private TouchEngineController() {}

    static synchronized String apply(String packageName, boolean fastTouch,
                                     boolean linearDrag, int requestedLevel) {
        int level = Math.max(1, Math.min(100, requestedLevel));
        if (!fastTouch && !linearDrag) return reset(packageName);

        StringBuilder out = new StringBuilder();
        activePackage = packageName;
        append(out, "TOUCH_ENGINE=UNIVERSAL_AOSP");
        append(out, "VENDOR_DEPENDENCY=NONE");

        if (fastTouch) {
            String gameMode = runAllowFailure("cmd game mode performance " + packageName);
            append(out, "AOSP_GAME_MODE=" + compact(gameMode));
            append(out, "FAST_TOUCH=AOSP_PERFORMANCE_PIPELINE");
        } else {
            append(out, "FAST_TOUCH=OFF");
        }

        if (linearDrag) append(out, linearDragStatus());
        else append(out, "LINEAR_DRAG=OFF");

        append(out, "LEVEL=" + level);
        return out.toString();
    }

    static synchronized String reset(String packageName) {
        StringBuilder out = new StringBuilder();
        append(out, "AOSP_GAME_MODE=" + compact(runAllowFailure("cmd game mode standard " + packageName)));
        if (packageName.equals(activePackage)) activePackage = null;
        append(out, "TOUCH_ENGINE=RESTORED");
        return out.toString();
    }

    private static String linearDragStatus() {
        String noResample = shellValue("getprop ro.input.noresample");
        if ("1".equals(noResample)) {
            return "LINEAR_DRAG=ROM_DISABLED_AOSP_RESAMPLING ROOT_FRAMEWORK_REQUIRED";
        }

        String legacy = shellValue("getprop ro.input.resampling");
        if ("0".equals(legacy)) {
            return "LINEAR_DRAG=ROM_DISABLED_AOSP_RESAMPLING ROOT_FRAMEWORK_REQUIRED";
        }

        String monitorPermission = runAllowFailure(
                "cmd package check-permission android.permission.MONITOR_INPUT com.android.shell");
        boolean monitorGranted = monitorPermission.toLowerCase().contains("granted");
        return monitorGranted
                ? "LINEAR_DRAG=AOSP_RESAMPLING_ACTIVE RAW_MONITOR_AVAILABLE"
                : "LINEAR_DRAG=AOSP_RESAMPLING_ACTIVE RAW_MONITOR_UNAVAILABLE";
    }

    private static String shellValue(String command) {
        String value = runAllowFailure(command);
        if (value.startsWith("ERR:")) return "";
        String clean = value.trim();
        return "null".equalsIgnoreCase(clean) ? "" : clean;
    }

    private static String compact(String value) {
        return value == null ? "" : value.replace('\n', ' ').trim();
    }

    private static void append(StringBuilder out, String value) {
        if (value == null || value.trim().isEmpty()) return;
        if (out.length() > 0) out.append('\n');
        out.append(value.trim());
    }

    private static String runAllowFailure(String command) {
        try {
            Process process = new ProcessBuilder("/system/bin/sh", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream in = process.getInputStream()) {
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) >= 0) buffer.write(chunk, 0, n);
            }
            int code = process.waitFor();
            String text = buffer.toString(StandardCharsets.UTF_8.name()).trim();
            if (code != 0) return "ERR:" + code + (text.isEmpty() ? "" : ":" + text.replace('\n', ' '));
            return text.isEmpty() ? "OK" : text;
        } catch (Exception e) {
            String message = e.getMessage();
            return "ERR:" + (message == null ? e.getClass().getSimpleName() : message.replace('\n', ' '));
        }
    }
}
