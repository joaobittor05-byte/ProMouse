package com.leo.optimazer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leo Frame Repeat / Multiple Sync.
 *
 * Não gera frames e não captura a imagem do jogo. O Android mantém o último buffer válido
 * até o próximo frame real chegar; o Leo casa o FPS real/alvo com um refresh múltiplo inteiro
 * sempre que possível. Assim, 60 FPS em 120 Hz vira a cadência A,A,B,B,C,C sem criar fila,
 * sem IA e sem esperar o próximo frame.
 *
 * AUTO mede averageFPS das camadas do pacote via SurfaceFlinger timestats. Se a ROM não
 * expuser a medição, usa 60 FPS como fallback seguro. A frequência escolhida é solicitada
 * via min_refresh_rate/peak_refresh_rate e restaurada ao sair do app.
 */
final class FrameMatchController {
    private static final Pattern RATE_PATTERN = Pattern.compile(
            "(?i)(?:refreshRate|vsyncRate|renderFrameRate|fps)\\s*[=:]\\s*([0-9]{2,3}(?:\\.[0-9]+)?)");
    private static final Pattern LAYER_PATTERN = Pattern.compile("(?m)^\\s*layerName\\s*=\\s*(.+)$");
    private static final Pattern FPS_PATTERN = Pattern.compile("(?m)^\\s*averageFPS\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern FRAMES_PATTERN = Pattern.compile("(?m)^\\s*totalFrames\\s*=\\s*([0-9]+)");

    private static String activePackage;
    private static String originalMinRefresh;
    private static String originalPeakRefresh;
    private static boolean refreshCaptured;
    private static int activeFps;
    private static float activeRefresh;
    private static int activeMultiple;
    private static int activeDuplicates;
    private static String activeCadence = "OFF";
    private static String activeFpsSource = "none";

    private FrameMatchController() {}

    static synchronized String apply(String packageName, int requestedFps) {
        int fps;
        String source;
        if (requestedFps <= 0) {
            FpsSample sample = samplePackageFps(packageName);
            fps = sample.fps;
            source = sample.source;
        } else {
            fps = clampFps(requestedFps);
            source = "manual";
        }

        if (!refreshCaptured) captureRefreshBaseline();

        List<Float> rates = detectSupportedRates();
        if (rates.isEmpty()) {
            Float configured = parsePositiveFloat(originalPeakRefresh);
            if (configured != null) rates.add(configured);
            rates.add(60f);
        }
        normalizeRates(rates);

        Match match = chooseMatch(fps, rates);
        String target = trimFloat(match.refresh);
        String peak = runAllowFailure("settings put system peak_refresh_rate " + target);
        String min = runAllowFailure("settings put system min_refresh_rate " + target);

        activePackage = packageName;
        activeFps = fps;
        activeRefresh = match.refresh;
        activeMultiple = match.multiple;
        activeDuplicates = Math.max(0, match.multiple - 1);
        activeCadence = cadenceLabel(match.multiple, match.integer);
        activeFpsSource = source;

        String verify = verifySettings(match.refresh) ? "true" : "false";
        return "FRAME_REPEAT_OK"
                + " package=" + packageName
                + " fps=" + fps
                + " source=" + source
                + " refresh=" + target
                + " ratio=" + ratioLabel(match.refresh, fps)
                + " integer=" + match.integer
                + " multiple=" + match.multiple
                + " repeat=x" + match.multiple
                + " duplicates=" + activeDuplicates
                + " cadence=" + activeCadence
                + " strategy=DISPLAY_BUFFER_HOLD"
                + " queue=0"
                + " verified=" + verify
                + " candidates=" + ratesLabel(rates)
                + " peak=" + compact(peak)
                + " min=" + compact(min);
    }

    static synchronized String status(String packageName) {
        if (activePackage == null || !activePackage.equals(packageName) || activeRefresh <= 0f) {
            return "FRAME_REPEAT_STATUS active=false package=" + packageName;
        }
        boolean verified = verifySettings(activeRefresh);
        return "FRAME_REPEAT_STATUS active=true"
                + " package=" + packageName
                + " fps=" + activeFps
                + " source=" + activeFpsSource
                + " refresh=" + trimFloat(activeRefresh)
                + " ratio=" + ratioLabel(activeRefresh, activeFps)
                + " multiple=" + activeMultiple
                + " repeat=x" + activeMultiple
                + " duplicates=" + activeDuplicates
                + " cadence=" + activeCadence
                + " strategy=DISPLAY_BUFFER_HOLD"
                + " queue=0"
                + " verified=" + verified;
    }

    static synchronized String reset(String packageName) {
        StringBuilder out = new StringBuilder();
        if (refreshCaptured) {
            append(out, "peak=" + restoreSetting("peak_refresh_rate", originalPeakRefresh));
            append(out, "min=" + restoreSetting("min_refresh_rate", originalMinRefresh));
        } else {
            append(out, "refresh=no_baseline");
        }
        runAllowFailure("dumpsys SurfaceFlinger --timestats -disable");

        refreshCaptured = false;
        originalMinRefresh = null;
        originalPeakRefresh = null;
        if (packageName.equals(activePackage)) activePackage = null;
        activeFps = 0;
        activeRefresh = 0f;
        activeMultiple = 0;
        activeDuplicates = 0;
        activeCadence = "OFF";
        activeFpsSource = "none";
        append(out, "FRAME_REPEAT=RESTORED");
        return out.toString();
    }

    private static FpsSample samplePackageFps(String packageName) {
        String start = runAllowFailure("dumpsys SurfaceFlinger --timestats -clear -enable");
        if (start.startsWith("ERR:")) return new FpsSample(60, "fallback60:timestats_unavailable");

        try {
            Thread.sleep(1100L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String dump = runAllowFailure("dumpsys SurfaceFlinger --timestats -dump");
        runAllowFailure("dumpsys SurfaceFlinger --timestats -disable");
        if (dump.startsWith("ERR:")) return new FpsSample(60, "fallback60:timestats_dump_failed");

        float bestFps = -1f;
        long bestFrames = -1L;
        Matcher layerMatcher = LAYER_PATTERN.matcher(dump);
        List<Integer> starts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        while (layerMatcher.find()) {
            starts.add(layerMatcher.start());
            names.add(layerMatcher.group(1).trim());
        }

        for (int i = 0; i < starts.size(); i++) {
            String name = names.get(i);
            if (!name.toLowerCase(Locale.ROOT).contains(packageName.toLowerCase(Locale.ROOT))) continue;
            int from = starts.get(i);
            int to = i + 1 < starts.size() ? starts.get(i + 1) : dump.length();
            String block = dump.substring(from, to);

            Matcher fpsMatcher = FPS_PATTERN.matcher(block);
            if (!fpsMatcher.find()) continue;
            float fps;
            try { fps = Float.parseFloat(fpsMatcher.group(1)); } catch (Exception e) { continue; }
            if (fps < 10f || fps > 240f) continue;

            long frames = 0L;
            Matcher framesMatcher = FRAMES_PATTERN.matcher(block);
            if (framesMatcher.find()) {
                try { frames = Long.parseLong(framesMatcher.group(1)); } catch (Exception ignored) {}
            }

            boolean surfaceView = name.contains("SurfaceView") || name.contains("BLAST");
            long scoreFrames = frames + (surfaceView ? 1_000_000L : 0L);
            if (scoreFrames > bestFrames) {
                bestFrames = scoreFrames;
                bestFps = fps;
            }
        }

        if (bestFps <= 0f) return new FpsSample(60, "fallback60:no_game_layer");
        return new FpsSample(snapFps(bestFps), "surfaceflinger:" + trimFloat(bestFps));
    }

    private static int snapFps(float measured) {
        int[] common = {24, 25, 30, 40, 45, 48, 50, 60, 72, 75, 90, 100, 120, 144, 165, 240};
        int best = Math.round(measured);
        float error = Float.MAX_VALUE;
        for (int target : common) {
            float e = Math.abs(measured - target) / target;
            if (e < error) {
                error = e;
                best = target;
            }
        }
        if (error <= 0.10f) return clampFps(best);
        return clampFps(Math.round(measured));
    }

    private static List<Float> detectSupportedRates() {
        List<Float> rates = new ArrayList<>();
        String dump = runAllowFailure("dumpsys display");
        if (!dump.startsWith("ERR:")) {
            Matcher matcher = RATE_PATTERN.matcher(dump);
            while (matcher.find()) {
                try {
                    float rate = Float.parseFloat(matcher.group(1));
                    if (rate >= 30f && rate <= 360f) rates.add(rate);
                } catch (Exception ignored) {}
            }
        }
        Float peak = parsePositiveFloat(shellValue("settings get system peak_refresh_rate"));
        Float min = parsePositiveFloat(shellValue("settings get system min_refresh_rate"));
        if (peak != null && peak >= 30f && peak <= 360f) rates.add(peak);
        if (min != null && min >= 30f && min <= 360f) rates.add(min);
        normalizeRates(rates);
        return rates;
    }

    private static void normalizeRates(List<Float> rates) {
        Collections.sort(rates);
        for (int i = rates.size() - 1; i > 0; i--) {
            if (Math.abs(rates.get(i) - rates.get(i - 1)) < 0.35f) rates.remove(i);
        }
    }

    private static Match chooseMatch(int fps, List<Float> rates) {
        List<Match> exact = new ArrayList<>();
        List<Match> fallback = new ArrayList<>();
        for (float rate : rates) {
            if (rate + 0.5f < fps) continue;
            float ratio = rate / fps;
            int nearest = Math.max(1, Math.round(ratio));
            float error = Math.abs(ratio - nearest);
            Match match = new Match(rate, nearest, error <= 0.035f, error);
            if (match.integer) exact.add(match); else fallback.add(match);
        }

        if (!exact.isEmpty()) {
            // Para Repeat, múltiplo inteiro é o ponto principal. Entre os exatos,
            // usa o refresh mais alto para reduzir o intervalo de scan/present.
            exact.sort(Comparator.comparingDouble((Match m) -> m.refresh).reversed());
            return exact.get(0);
        }

        if (!fallback.isEmpty()) {
            // Se a tela não possui múltiplo perfeito, escolhe primeiro a menor
            // irregularidade de cadência; em empate, o refresh mais alto.
            fallback.sort(Comparator
                    .comparingDouble((Match m) -> m.error)
                    .thenComparing(Comparator.comparingDouble((Match m) -> m.refresh).reversed()));
            return fallback.get(0);
        }

        float max = rates.isEmpty() ? 60f : rates.get(rates.size() - 1);
        int multiple = Math.max(1, Math.round(max / Math.max(1, fps)));
        return new Match(max, multiple, false, 1f);
    }

    private static void captureRefreshBaseline() {
        originalMinRefresh = shellValue("settings get system min_refresh_rate");
        originalPeakRefresh = shellValue("settings get system peak_refresh_rate");
        refreshCaptured = true;
    }

    private static boolean verifySettings(float target) {
        Float peak = parsePositiveFloat(shellValue("settings get system peak_refresh_rate"));
        Float min = parsePositiveFloat(shellValue("settings get system min_refresh_rate"));
        return peak != null && min != null
                && Math.abs(peak - target) < 0.6f
                && Math.abs(min - target) < 0.6f;
    }

    private static String restoreSetting(String key, String value) {
        if (value == null || value.isEmpty() || "null".equalsIgnoreCase(value)) {
            return runAllowFailure("settings delete system " + key);
        }
        return runAllowFailure("settings put system " + key + " " + value);
    }

    private static int clampFps(int fps) {
        return Math.max(20, Math.min(240, fps));
    }

    private static String ratioLabel(float refresh, int fps) {
        if (fps <= 0) return "?";
        float ratio = refresh / fps;
        int n = Math.max(1, Math.round(ratio));
        if (Math.abs(ratio - n) <= 0.035f) return n + ":1";
        return String.format(Locale.US, "%.2f:1", ratio);
    }

    private static String cadenceLabel(int multiple, boolean integer) {
        if (!integer || multiple <= 0) return "ADAPTIVE";
        if (multiple == 1) return "A,B,C,D";
        if (multiple == 2) return "A,A,B,B";
        if (multiple == 3) return "A,A,A,B,B,B";
        if (multiple == 4) return "A,A,A,A,B,B,B,B";
        return "HOLD_X" + multiple;
    }

    private static String ratesLabel(List<Float> rates) {
        StringBuilder b = new StringBuilder();
        for (float rate : rates) {
            if (b.length() > 0) b.append('/');
            b.append(trimFloat(rate));
        }
        return b.toString();
    }

    private static String compact(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ').trim();
    }

    private static String shellValue(String command) {
        String value = runAllowFailure(command);
        if (value.startsWith("ERR:")) return "";
        String clean = value.trim();
        return "null".equalsIgnoreCase(clean) ? "" : clean;
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
        if (Math.abs(value - Math.round(value)) < 0.01f) return String.valueOf(Math.round(value));
        return String.format(Locale.US, "%.2f", value);
    }

    private static void append(StringBuilder out, String value) {
        if (value == null || value.trim().isEmpty()) return;
        if (out.length() > 0) out.append(' ');
        out.append(value.trim());
    }

    private static String runAllowFailure(String command) {
        try {
            Process process = new ProcessBuilder("/system/bin/sh", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream in = process.getInputStream()) {
                byte[] chunk = new byte[8192];
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

    private static final class FpsSample {
        final int fps;
        final String source;
        FpsSample(int fps, String source) { this.fps = fps; this.source = source; }
    }

    private static final class Match {
        final float refresh;
        final int multiple;
        final boolean integer;
        final float error;
        Match(float refresh, int multiple, boolean integer, float error) {
            this.refresh = refresh;
            this.multiple = multiple;
            this.integer = integer;
            this.error = error;
        }
    }
}
