package com.leo.optimazer;

import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PerAppCompat {
    public static final int VIRTUAL_DPI_BASE = 800;
    private static final int[] FACTORS = {30,35,40,45,50,55,60,65,70,75,80,85,90};

    // Faixa de segurança do Leo para a menor dimensão lógica do app.
    // Não é um limite físico do painel: serve para impedir perfis extremos que quebram a UI.
    private static final int SAFE_MIN_SHORT_DP = 320;
    private static final int SAFE_MAX_SHORT_DP = 900;
    private static final int MIN_ANDROID_DENSITY = 72;
    private static final int MAX_ANDROID_DENSITY = 1000;
    private static final int MIN_VIRTUAL_DPI = 200;
    private static final int MAX_VIRTUAL_DPI = 6400;

    public static final class DpiLimits {
        public final int minDpi;
        public final int maxDpi;
        public final int recommendedDpi;
        public final int estimatedWidth;
        public final int estimatedHeight;
        public final int factorPercent;
        public final boolean inverse;
        public final int equivalentAndroidDensity;

        DpiLimits(int minDpi, int maxDpi, int recommendedDpi,
                  int estimatedWidth, int estimatedHeight,
                  int factorPercent, boolean inverse,
                  int equivalentAndroidDensity) {
            this.minDpi = minDpi;
            this.maxDpi = maxDpi;
            this.recommendedDpi = recommendedDpi;
            this.estimatedWidth = estimatedWidth;
            this.estimatedHeight = estimatedHeight;
            this.factorPercent = factorPercent;
            this.inverse = inverse;
            this.equivalentAndroidDensity = equivalentAndroidDensity;
        }

        public int clamp(int virtualDpi) {
            return Math.max(minDpi, Math.min(maxDpi, virtualDpi));
        }

        public String label() {
            return "DPI Virtual segura nesta resolução: " + minDpi + "–" + maxDpi;
        }
    }

    public static final class Plan {
        public final boolean enabled;
        public final boolean inverse;
        public final int factorPercent;
        public final int estimatedWidth;
        public final int estimatedHeight;
        // Densidade Android que será aplicada NA TAREFA do app pelo Shizuku.
        public final int estimatedDensity;
        public final int estimatedVirtualDpi;
        // Estes dois campos mantêm compatibilidade com a UI atual e agora representam
        // os limites de DPI Virtual, não densidade Android.
        public final int minAllowedDensity;
        public final int maxAllowedDensity;
        public final int requestedDensity;
        public final int normalizedDensity;
        public final String command;
        public final String summary;

        Plan(boolean enabled, boolean inverse, int factorPercent,
             int estimatedWidth, int estimatedHeight,
             int estimatedDensity, int estimatedVirtualDpi,
             int minAllowedDensity, int maxAllowedDensity,
             int requestedDensity, int normalizedDensity,
             String command, String summary) {
            this.enabled = enabled;
            this.inverse = inverse;
            this.factorPercent = factorPercent;
            this.estimatedWidth = estimatedWidth;
            this.estimatedHeight = estimatedHeight;
            this.estimatedDensity = estimatedDensity;
            this.estimatedVirtualDpi = estimatedVirtualDpi;
            this.minAllowedDensity = minAllowedDensity;
            this.maxAllowedDensity = maxAllowedDensity;
            this.requestedDensity = requestedDensity;
            this.normalizedDensity = normalizedDensity;
            this.command = command;
            this.summary = summary;
        }
    }

    private static final class Candidate {
        final boolean inverse;
        final int factorPercent;
        final double scale;
        final int width;
        final int height;
        final int baseAndroidDensity;
        final double resolutionError;

        Candidate(boolean inverse, int factorPercent, double scale,
                  int width, int height, int baseAndroidDensity,
                  double resolutionError) {
            this.inverse = inverse;
            this.factorPercent = factorPercent;
            this.scale = scale;
            this.width = width;
            this.height = height;
            this.baseAndroidDensity = baseAndroidDensity;
            this.resolutionError = resolutionError;
        }
    }

    private PerAppCompat() {}

    // 800 = densidade base da resolução do app; 1600 = metade da densidade Android;
    // 2400 = aproximadamente um terço. Isso NÃO escolhe nem altera a resolução.
    public static int androidDensityFromVirtual(int virtualDpi, int baseAndroidDensity) {
        int safeVirtual = Math.max(1, virtualDpi);
        int density = (int) Math.round(baseAndroidDensity * (VIRTUAL_DPI_BASE / (double) safeVirtual));
        return Math.max(MIN_ANDROID_DENSITY, Math.min(MAX_ANDROID_DENSITY, density));
    }

    public static int virtualFromAndroidDensity(int androidDensity, int baseAndroidDensity) {
        int safeAndroid = Math.max(1, androidDensity);
        int virtual = (int) Math.round(VIRTUAL_DPI_BASE * (baseAndroidDensity / (double) safeAndroid));
        return Math.max(MIN_VIRTUAL_DPI, Math.min(MAX_VIRTUAL_DPI, virtual));
    }

    public static DpiLimits limitsForResolution(int requestedW, int requestedH, DisplayMetrics nativeMetrics) {
        Candidate resolution = bestResolutionCandidate(candidates(
                Math.max(1, nativeMetrics.widthPixels),
                Math.max(1, nativeMetrics.heightPixels),
                Math.max(1, nativeMetrics.densityDpi),
                Math.max(1, requestedW),
                Math.max(1, requestedH)
        ));

        int shortPx = Math.max(1, Math.min(resolution.width, resolution.height));
        int minAndroidDensity = Math.max(MIN_ANDROID_DENSITY,
                (int) Math.ceil(shortPx * 160.0 / SAFE_MAX_SHORT_DP));
        int maxAndroidDensity = Math.min(MAX_ANDROID_DENSITY,
                (int) Math.floor(shortPx * 160.0 / SAFE_MIN_SHORT_DP));
        if (maxAndroidDensity < minAndroidDensity) maxAndroidDensity = minAndroidDensity;

        int minVirtual = virtualFromAndroidDensity(maxAndroidDensity, resolution.baseAndroidDensity);
        int maxVirtual = virtualFromAndroidDensity(minAndroidDensity, resolution.baseAndroidDensity);
        if (maxVirtual < minVirtual) {
            int t = minVirtual;
            minVirtual = maxVirtual;
            maxVirtual = t;
        }
        minVirtual = Math.max(MIN_VIRTUAL_DPI, minVirtual);
        maxVirtual = Math.min(MAX_VIRTUAL_DPI, maxVirtual);

        int recommended = Math.max(minVirtual, Math.min(maxVirtual, VIRTUAL_DPI_BASE));
        int equivalent = androidDensityFromVirtual(recommended, resolution.baseAndroidDensity);

        return new DpiLimits(
                minVirtual,
                maxVirtual,
                recommended,
                resolution.width,
                resolution.height,
                resolution.factorPercent,
                resolution.inverse,
                equivalent
        );
    }

    public static Plan build(ProfileStore.Profile profile, DisplayMetrics nativeMetrics) {
        int nativeW = Math.max(1, nativeMetrics.widthPixels);
        int nativeH = Math.max(1, nativeMetrics.heightPixels);
        int nativeDpi = Math.max(1, nativeMetrics.densityDpi);

        Candidate resolution = bestResolutionCandidate(candidates(
                nativeW, nativeH, nativeDpi, profile.width, profile.height));
        DpiLimits limits = limitsForResolution(profile.width, profile.height, nativeMetrics);
        int normalizedVirtual = limits.clamp(profile.density);
        int targetTaskDensity = androidDensityFromVirtual(normalizedVirtual, resolution.baseAndroidDensity);

        if (!profile.enabled) {
            String summary = limits.label() + " • perfil desativado • resolução e DPI restauradas";
            return new Plan(false, false, 100,
                    nativeW, nativeH, nativeDpi, VIRTUAL_DPI_BASE,
                    limits.minDpi, limits.maxDpi,
                    profile.density, normalizedVirtual,
                    resetCommand(profile.packageName), summary);
        }

        // A resolução é escolhida SOMENTE pela resolução solicitada. A DPI não participa
        // desta decisão, logo mudar 800 -> 1600 -> 2400 nunca troca o fator de resolução.
        String resolutionCommand;
        if (Math.abs(resolution.scale - 1.0) < 0.01) {
            resolutionCommand = resetResolutionCommand(profile.packageName);
        } else {
            StringBuilder builder = new StringBuilder();
            builder.append(resetResolutionCommand(profile.packageName)).append("; ");
            builder.append("am compat enable ")
                    .append(resolution.inverse ? "DOWNSCALED_INVERSE" : "DOWNSCALED")
                    .append(' ').append(profile.packageName).append("; ");
            builder.append("am compat enable DOWNSCALE_").append(resolution.factorPercent)
                    .append(' ').append(profile.packageName).append("; ");
            builder.append("am force-stop ").append(profile.packageName);
            resolutionCommand = builder.toString();
        }

        String dpiAdjustment = profile.density == normalizedVirtual
                ? "DPI Virtual " + profile.density
                : "DPI Virtual " + profile.density + " → limitado a " + normalizedVirtual;

        String summary = String.format(Locale.US,
                "resolução independente ~%d×%d • escala %s%d%% • %s • densidade Android da tarefa ~%d • DPI não altera a resolução",
                resolution.width,
                resolution.height,
                resolution.inverse ? "1/" : "",
                resolution.factorPercent,
                dpiAdjustment,
                targetTaskDensity);

        return new Plan(true, resolution.inverse, resolution.factorPercent,
                resolution.width, resolution.height,
                targetTaskDensity, normalizedVirtual,
                limits.minDpi, limits.maxDpi,
                profile.density, normalizedVirtual,
                resolutionCommand, summary);
    }

    public static String resetCommand(String packageName) {
        return resetResolutionCommand(packageName) + "; leo density-reset " + packageName;
    }

    private static String resetResolutionCommand(String packageName) {
        StringBuilder command = new StringBuilder();
        command.append("am compat reset DOWNSCALED ").append(packageName);
        command.append("; am compat reset DOWNSCALED_INVERSE ").append(packageName);
        for (int factor : FACTORS) {
            command.append("; am compat reset DOWNSCALE_").append(factor)
                    .append(' ').append(packageName);
        }
        command.append("; am force-stop ").append(packageName);
        return command.toString();
    }

    private static List<Candidate> candidates(int nativeW, int nativeH, int nativeDpi,
                                              int targetW, int targetH) {
        List<Candidate> result = new ArrayList<>();
        result.add(candidate(false, 100, 1.0, nativeW, nativeH, nativeDpi, targetW, targetH));
        for (int factor : FACTORS) {
            double down = factor / 100.0;
            result.add(candidate(false, factor, down, nativeW, nativeH, nativeDpi, targetW, targetH));
            double inverse = 100.0 / factor;
            result.add(candidate(true, factor, inverse, nativeW, nativeH, nativeDpi, targetW, targetH));
        }
        return result;
    }

    private static Candidate candidate(boolean inverse, int factor, double scale,
                                       int nativeW, int nativeH, int nativeDpi,
                                       int targetW, int targetH) {
        int width = (int) Math.round(nativeW * scale);
        int height = (int) Math.round(nativeH * scale);
        int baseDensity = Math.max(MIN_ANDROID_DENSITY,
                Math.min(MAX_ANDROID_DENSITY, (int) Math.round(nativeDpi * scale)));
        return new Candidate(inverse, factor, scale, width, height, baseDensity,
                resolutionError(width, height, targetW, targetH));
    }

    private static Candidate bestResolutionCandidate(List<Candidate> candidates) {
        Candidate best = candidates.get(0);
        for (Candidate candidate : candidates) {
            if (candidate.resolutionError < best.resolutionError) best = candidate;
        }
        return best;
    }

    private static double resolutionError(int actualW, int actualH, int targetW, int targetH) {
        int actualShort = Math.min(actualW, actualH);
        int actualLong = Math.max(actualW, actualH);
        int targetShort = Math.min(targetW, targetH);
        int targetLong = Math.max(targetW, targetH);
        double shortError = Math.abs(actualShort - targetShort) / (double) Math.max(1, targetShort);
        double longError = Math.abs(actualLong - targetLong) / (double) Math.max(1, targetLong);
        return Math.max(shortError, longError);
    }
}
