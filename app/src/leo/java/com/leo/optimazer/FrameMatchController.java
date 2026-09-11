package com.leo.optimazer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leo Frame Repeat / Frame Match universal.
 * Não gera imagens: mantém o último buffer até o próximo frame real e tenta casar FPS/Hz.
 */
final class FrameMatchController {
    private static final Pattern RATE_PATTERN = Pattern.compile("(?i)(?:refreshRate|vsyncRate|renderFrameRate|fps)\\s*[=:]\\s*([0-9]{2,3}(?:\\.[0-9]+)?)");
    private static final Pattern LAYER_PATTERN = Pattern.compile("(?m)^\\s*layerName\\s*=\\s*(.+)$");
    private static final Pattern FPS_PATTERN = Pattern.compile("(?m)^\\s*averageFPS\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern FRAMES_PATTERN = Pattern.compile("(?m)^\\s*totalFrames\\s*=\\s*([0-9]+)");

    private static String activePackage;
    private static String activeMode = FrameRepeatPrefs.MODE_COMPETITIVE;
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
        return apply(packageName, requestedFps, FrameRepeatPrefs.MODE_COMPETITIVE);
    }

    static synchronized String apply(String packageName, int requestedFps, String requestedMode) {
        String mode = FrameRepeatPrefs.normalizeMode(requestedMode);
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

        Match match = chooseMatch(fps, rates, mode);
        String target = trimFloat(match.refresh);
        String peak = run("settings put system peak_refresh_rate " + target);
        String min = run("settings put system min_refresh_rate " + target);

        activePackage = packageName;
        activeMode = mode;
        activeFps = fps;
        activeRefresh = match.refresh;
        activeMultiple = match.multiple;
        activeDuplicates = Math.max(0, match.multiple - 1);
        activeCadence = cadenceLabel(match.multiple, match.integer);
        activeFpsSource = source;

        return "FRAME_REPEAT_OK"
                + " package=" + packageName
                + " mode=" + mode
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
                + " verified=" + verifySettings(match.refresh)
                + " candidates=" + ratesLabel(rates)
                + " peak=" + compact(peak)
                + " min=" + compact(min);
    }

    static synchronized String status(String packageName) {
        if (activePackage == null || !activePackage.equals(packageName) || activeRefresh <= 0f) {
            return "FRAME_REPEAT_STATUS active=false package=" + packageName;
        }
        return "FRAME_REPEAT_STATUS active=true"
                + " package=" + packageName
                + " mode=" + activeMode
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
                + " verified=" + verifySettings(activeRefresh);
    }

    static synchronized String reset(String packageName) {
        StringBuilder out = new StringBuilder();
        if (refreshCaptured) {
            append(out, "peak=" + restoreSetting("peak_refresh_rate", originalPeakRefresh));
            append(out, "min=" + restoreSetting("min_refresh_rate", originalMinRefresh));
        } else append(out, "refresh=no_baseline");
        run("dumpsys SurfaceFlinger --timestats -disable");
        refreshCaptured = false;
        originalMinRefresh = null;
        originalPeakRefresh = null;
        if (packageName.equals(activePackage)) activePackage = null;
        activeMode = FrameRepeatPrefs.MODE_COMPETITIVE;
        activeFps = 0;
        activeRefresh = 0f;
        activeMultiple = 0;
        activeDuplicates = 0;
        activeCadence = "OFF";
        activeFpsSource = "none";
        append(out, "FRAME_REPEAT=RESTORED");
        return out.toString();
    }

    private static Match chooseMatch(int fps, List<Float> rates, String mode) {
        List<Match> candidates = new ArrayList<>();
        for (float rate : rates) {
            if (rate + 0.5f < fps) continue;
            float ratio = rate / Math.max(1, fps);
            int nearest = Math.max(1, Math.round(ratio));
            float error = Math.abs(ratio - nearest);
            candidates.add(new Match(rate, nearest, error <= 0.035f, error));
        }
        if (candidates.isEmpty()) {
            float max = rates.isEmpty() ? 60f : rates.get(rates.size() - 1);
            int multiple = Math.max(1, Math.round(max / Math.max(1, fps)));
            return new Match(max, multiple, false, 1f);
        }

        if (FrameRepeatPrefs.MODE_COMPETITIVE.equals(mode)) {
            // Menor latência percebida: maior refresh disponível. Frame real sempre tem prioridade.
            candidates.sort(Comparator.comparingDouble((Match m) -> m.refresh).reversed());
            return candidates.get(0);
        }

        List<Match> exact = new ArrayList<>();
        for (Match m : candidates) if (m.integer) exact.add(m);

        if (FrameRepeatPrefs.MODE_SMOOTH.equals(mode) && !exact.isEmpty()) {
            // Prefere repetição moderada (x2), depois menor erro e maior refresh.
            exact.sort(Comparator
                    .comparingInt((Match m) -> Math.abs(m.multiple - 2))
                    .thenComparingDouble(m -> m.error)
                    .thenComparing(Comparator.comparingDouble((Match m) -> m.refresh).reversed()));
            return exact.get(0);
        }

        if (!exact.isEmpty()) {
            // Qualidade: múltiplo perfeito e maior frequência entre os perfeitos.
            exact.sort(Comparator.comparingDouble((Match m) -> m.refresh).reversed());
            return exact.get(0);
        }

        candidates.sort(Comparator
                .comparingDouble((Match m) -> m.error)
                .thenComparing(Comparator.comparingDouble((Match m) -> m.refresh).reversed()));
        return candidates.get(0);
    }

    private static FpsSample samplePackageFps(String packageName) {
        String start = run("dumpsys SurfaceFlinger --timestats -clear -enable");
        if (start.startsWith("ERR:")) return new FpsSample(60, "fallback60:timestats_unavailable");
        try { Thread.sleep(900L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        String dump = run("dumpsys SurfaceFlinger --timestats -dump");
        run("dumpsys SurfaceFlinger --timestats -disable");
        if (dump.startsWith("ERR:")) return new FpsSample(60, "fallback60:timestats_dump_failed");

        float bestFps = -1f;
        long bestFrames = -1L;
        Matcher layerMatcher = LAYER_PATTERN.matcher(dump);
        List<Integer> starts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        while (layerMatcher.find()) { starts.add(layerMatcher.start()); names.add(layerMatcher.group(1).trim()); }
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
            if (framesMatcher.find()) try { frames = Long.parseLong(framesMatcher.group(1)); } catch (Exception ignored) {}
            boolean surfaceView = name.contains("SurfaceView") || name.contains("BLAST");
            long score = frames + (surfaceView ? 1_000_000L : 0L);
            if (score > bestFrames) { bestFrames = score; bestFps = fps; }
        }
        if (bestFps <= 0f) return new FpsSample(60, "fallback60:no_game_layer");
        return new FpsSample(snapFps(bestFps), "surfaceflinger:" + trimFloat(bestFps));
    }

    private static List<Float> detectSupportedRates() {
        List<Float> rates = new ArrayList<>();
        String dump = run("dumpsys display");
        if (!dump.startsWith("ERR:")) {
            Matcher matcher = RATE_PATTERN.matcher(dump);
            while (matcher.find()) try {
                float rate = Float.parseFloat(matcher.group(1));
                if (rate >= 30f && rate <= 360f) rates.add(rate);
            } catch (Exception ignored) {}
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
        for (int i = rates.size() - 1; i > 0; i--) if (Math.abs(rates.get(i) - rates.get(i - 1)) < 0.35f) rates.remove(i);
    }
    private static int snapFps(float measured) {
        int[] common = {24,25,30,40,45,48,50,60,72,75,90,100,120,144,165,240};
        int best = Math.round(measured); float error = Float.MAX_VALUE;
        for (int target : common) { float e = Math.abs(measured - target) / target; if (e < error) { error = e; best = target; } }
        return clampFps(error <= 0.10f ? best : Math.round(measured));
    }
    private static void captureRefreshBaseline() {
        originalMinRefresh = shellValue("settings get system min_refresh_rate");
        originalPeakRefresh = shellValue("settings get system peak_refresh_rate");
        refreshCaptured = true;
    }
    private static boolean verifySettings(float target) {
        Float peak = parsePositiveFloat(shellValue("settings get system peak_refresh_rate"));
        Float min = parsePositiveFloat(shellValue("settings get system min_refresh_rate"));
        return peak != null && min != null && Math.abs(peak - target) < 0.6f && Math.abs(min - target) < 0.6f;
    }
    private static String restoreSetting(String key, String value) {
        return value == null || value.isEmpty() || "null".equalsIgnoreCase(value)
                ? run("settings delete system " + key)
                : run("settings put system " + key + " " + value);
    }
    private static String shellValue(String command) {
        String value = run(command);
        if (value.startsWith("ERR:")) return "";
        String clean = value.trim();
        return "null".equalsIgnoreCase(clean) ? "" : clean;
    }
    private static String run(String command) { return PrivilegedShell.runAllowFailure(command); }
    private static int clampFps(int fps) { return Math.max(20, Math.min(240, fps)); }
    private static String ratioLabel(float refresh, int fps) {
        if (fps <= 0) return "?";
        float ratio = refresh / fps; int n = Math.max(1, Math.round(ratio));
        return Math.abs(ratio - n) <= 0.035f ? n + ":1" : String.format(Locale.US, "%.2f:1", ratio);
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
        for (float rate : rates) { if (b.length() > 0) b.append('/'); b.append(trimFloat(rate)); }
        return b.toString();
    }
    private static String compact(String value) { return value == null ? "" : value.replace('\n', ' ').trim(); }
    private static Float parsePositiveFloat(String value) { try { if (value == null || value.trim().isEmpty()) return null; float f = Float.parseFloat(value.trim()); return f > 0f ? f : null; } catch (Exception ignored) { return null; } }
    private static String trimFloat(float value) { return Math.abs(value - Math.round(value)) < 0.01f ? String.valueOf(Math.round(value)) : String.format(Locale.US, "%.2f", value); }
    private static void append(StringBuilder out, String value) { if (value == null || value.trim().isEmpty()) return; if (out.length() > 0) out.append(' '); out.append(value.trim()); }

    private static final class FpsSample { final int fps; final String source; FpsSample(int fps, String source) { this.fps = fps; this.source = source; } }
    private static final class Match { final float refresh; final int multiple; final boolean integer; final float error; Match(float refresh, int multiple, boolean integer, float error) { this.refresh = refresh; this.multiple = multiple; this.integer = integer; this.error = error; } }
}
