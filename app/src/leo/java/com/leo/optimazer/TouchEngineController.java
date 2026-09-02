package com.leo.optimazer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Touch Engine universal executado dentro do UserService do Shizuku.
 *
 * Não depende de Game Turbo, Xiaomi, Samsung Game Booster ou qualquer HAL de fabricante.
 * Usa apenas mecanismos AOSP/shell que podem existir em diferentes marcas:
 *  - Game Mode performance por pacote (quando suportado pelo Android do aparelho);
 *  - maior taxa de atualização disponível enquanto o perfil estiver em primeiro plano;
 *  - resampling nativo do Android para suavidade do arrasto, quando habilitado pela ROM.
 *
 * O Shizuku em modo shell possui INJECT_EVENTS, mas não recebe de forma universal
 * MONITOR_INPUT. Portanto o Leo não tenta duplicar/injetar uma segunda trajetória por cima
 * do dedo físico. Um filtro real de coordenadas exige root/framework hook ou uma permissão
 * de sistema que a ROM conceda explicitamente.
 */
final class TouchEngineController {
    private static final Pattern REFRESH_PATTERN = Pattern.compile(
            "(?i)(?:refreshRate|vsyncRate|renderFrameRate|fps)\\s*[=:]\\s*([0-9]{2,3}(?:\\.[0-9]+)?)");

    private static String activePackage;
    private static String originalMinRefresh;
    private static String originalPeakRefresh;
    private static boolean refreshCaptured;

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
            append(out, "AOSP_GAME_MODE=" + gameMode);
            append(out, applyRefreshLock(level));
            append(out, "FAST_TOUCH=AOSP_PERFORMANCE_PIPELINE");
        } else {
            append(out, "FAST_TOUCH=OFF");
        }

        if (linearDrag) {
            append(out, linearDragStatus());
        } else {
            append(out, "LINEAR_DRAG=OFF");
        }

        append(out, "LEVEL=" + level);
        return out.toString();
    }

    static synchronized String reset(String packageName) {
        StringBuilder out = new StringBuilder();
        append(out, "AOSP_GAME_MODE=" + runAllowFailure("cmd game mode standard " + packageName));
        append(out, restoreRefreshLock());
        if (packageName.equals(activePackage)) activePackage = null;
        append(out, "TOUCH_ENGINE=RESTORED");
        return out.toString();
    }

    /**
     * O Android faz resampling de coordenadas no InputConsumer para alinhar o movimento
     * ao VSYNC. Em builds atuais, ro.input.noresample=1 desliga esse comportamento.
     * Como a propriedade ro.* é somente leitura em builds de produção, o Shizuku não deve
     * fingir que consegue ativá-la quando a ROM a desabilitou.
     */
    private static String linearDragStatus() {
        String noResample = shellValue("getprop ro.input.noresample");
        if ("1".equals(noResample)) {
            return "LINEAR_DRAG=ROM_DISABLED_AOSP_RESAMPLING • ROOT_FRAMEWORK_REQUIRED";
        }

        String legacy = shellValue("getprop ro.input.resampling");
        if ("0".equals(legacy)) {
            return "LINEAR_DRAG=ROM_DISABLED_AOSP_RESAMPLING • ROOT_FRAMEWORK_REQUIRED";
        }

        String monitorPermission = runAllowFailure(
                "cmd package check-permission android.permission.MONITOR_INPUT com.android.shell");
        boolean monitorGranted = monitorPermission.toLowerCase().contains("granted");

        if (monitorGranted) {
            return "LINEAR_DRAG=AOSP_RESAMPLING_ACTIVE • RAW_MONITOR_AVAILABLE";
        }
        return "LINEAR_DRAG=AOSP_RESAMPLING_ACTIVE • RAW_MONITOR_UNAVAILABLE";
    }

    private static String applyRefreshLock(int level) {
        if (!refreshCaptured) {
            originalMinRefresh = shellValue("settings get system min_refresh_rate");
            originalPeakRefresh = shellValue("settings get system peak_refresh_rate");
            refreshCaptured = true;
        }

        float max = detectMaxRefreshRate();
        if (max < 60f) {
            Float original = parsePositiveFloat(originalPeakRefresh);
            max = original == null ? 60f : original;
        }

        // Intensidade baixa não precisa forçar o teto. A partir de 50, usa o maior modo.
        float target;
        if (level >= 50) {
            target = max;
        } else {
            target = 60f + (Math.max(60f, max) - 60f) * (level / 50f);
        }
        target = Math.max(60f, Math.min(max, target));

        String targetText = trimFloat(target);
        String peak = runAllowFailure("settings put system peak_refresh_rate " + targetText);
        String min = runAllowFailure("settings put system min_refresh_rate " + targetText);
        return "REFRESH_PRIORITY=" + targetText + "Hz peak:" + peak + " min:" + min
                + " max_detected=" + trimFloat(max) + "Hz";
    }

    private static float detectMaxRefreshRate() {
        String dump = runAllowFailure("dumpsys display");
        if (dump.startsWith("ERR:")) return -1f;

        float best = -1f;
        Matcher matcher = REFRESH_PATTERN.matcher(dump);
        while (matcher.find()) {
            try {
                float value = Float.parseFloat(matcher.group(1));
                if (value >= 30f && value <= 360f && value > best) best = value;
            } catch (Exception ignored) {}
        }

        Float configured = parsePositiveFloat(originalPeakRefresh);
        if (configured != null && configured > best && configured <= 360f) best = configured;
        return best;
    }

    private static String restoreRefreshLock() {
        if (!refreshCaptured) return "REFRESH_RESTORE=NO_BASELINE";
        String peak = restoreSetting("peak_refresh_rate", originalPeakRefresh);
        String min = restoreSetting("min_refresh_rate", originalMinRefresh);
        refreshCaptured = false;
        originalPeakRefresh = null;
        originalMinRefresh = null;
        return "REFRESH_RESTORE=peak:" + peak + " min:" + min;
    }

    private static String restoreSetting(String key, String value) {
        if (value == null || value.isEmpty() || "null".equalsIgnoreCase(value)) {
            return runAllowFailure("settings delete system " + key);
        }
        return runAllowFailure("settings put system " + key + " " + value);
    }

    private static String shellValue(String command) {
        String value = runAllowFailure(command);
        if (value.startsWith("ERR:")) return "";
        if ("null".equalsIgnoreCase(value.trim())) return "";
        return value.trim();
    }

    private static Float parsePositiveFloat(String value) {
        try {
            if (value == null || value.trim().isEmpty()) return null;
            float f = Float.parseFloat(value.trim());
            return f > 0f ? f : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String trimFloat(float value) {
        if (Math.abs(value - Math.round(value)) < 0.001f) return String.valueOf(Math.round(value));
        return String.format(java.util.Locale.US, "%.2f", value);
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
            return text.isEmpty() ? "OK" : text.replace('\n', ' ');
        } catch (Exception e) {
            String message = e.getMessage();
            return "ERR:" + (message == null ? e.getClass().getSimpleName() : message.replace('\n', ' '));
        }
    }
}
