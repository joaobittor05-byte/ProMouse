package com.leo.optimazer;

import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Perfil por aplicativo com DPI Android dedicada vinculada à qualidade.
 *
 * Regra:
 *  - 400 DPI = resolução-base salva no perfil.
 *  - DPI > 400 aumenta a resolução-alvo progressivamente.
 *  - DPI < 400 reduz a resolução-alvo progressivamente.
 *  - A curva usa sqrt(DPI / 400) para não tornar a carga da GPU extrema.
 *
 * A resolução efetiva continua usando o mecanismo de compatibilidade por pacote
 * disponível no Android, então o valor final é aproximado ao fator suportado mais próximo.
 */
public final class PerAppCompat {
    public static final int DEFAULT_DEDICATED_DPI = 400;
    public static final int MIN_DEDICATED_DPI = 160;
    public static final int MAX_DEDICATED_DPI = 1000;

    private static final int MIN_EFFECTIVE_PX = 320;
    private static final int MAX_EFFECTIVE_PX = 7680;
    private static final int[] FACTORS = {30,35,40,45,50,55,60,65,70,75,80,85,90};

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

        public int clamp(int dpi) {
            return Math.max(minDpi, Math.min(maxDpi, dpi));
        }

        public String label() {
            return "DPI dedicada segura para esta resolução-base: " + minDpi + "–" + maxDpi;
        }
    }

    public static final class Plan {
        public final boolean enabled;
        public final boolean inverse;
        public final int factorPercent;
        public final int estimatedWidth;
        public final int estimatedHeight;
        public final int estimatedDensity;
        // Mantido para compatibilidade com as telas/serviços atuais. Agora representa a DPI dedicada.
        public final int estimatedVirtualDpi;
        public final int minAllowedDensity;
        public final int maxAllowedDensity;
        public final int requestedDensity;
        public final int normalizedDensity;
        public final String command;
        public final String summary;

        Plan(boolean enabled, boolean inverse, int factorPercent,
             int estimatedWidth, int estimatedHeight,
             int estimatedDensity, int estimatedDedicatedDpi,
             int minAllowedDensity, int maxAllowedDensity,
             int requestedDensity, int normalizedDensity,
             String command, String summary) {
            this.enabled = enabled;
            this.inverse = inverse;
            this.factorPercent = factorPercent;
            this.estimatedWidth = estimatedWidth;
            this.estimatedHeight = estimatedHeight;
            this.estimatedDensity = estimatedDensity;
            this.estimatedVirtualDpi = estimatedDedicatedDpi;
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
        final double resolutionError;

        Candidate(boolean inverse, int factorPercent, double scale,
                  int width, int height, double resolutionError) {
            this.inverse = inverse;
            this.factorPercent = factorPercent;
            this.scale = scale;
            this.width = width;
            this.height = height;
            this.resolutionError = resolutionError;
        }
    }

    private PerAppCompat() {}

    /**
     * Curva balanceada: dobrar a DPI aumenta cada eixo por sqrt(2),
     * portanto aproximadamente dobra a quantidade total de pixels renderizados,
     * em vez de quadruplicá-la.
     */
    public static double qualityScaleForDpi(int dedicatedDpi) {
        int safe = Math.max(MIN_DEDICATED_DPI, Math.min(MAX_DEDICATED_DPI, dedicatedDpi));
        return Math.sqrt(safe / (double) DEFAULT_DEDICATED_DPI);
    }

    public static int linkedWidth(int baseWidth, int dedicatedDpi) {
        return clampPx((int) Math.round(Math.max(1, baseWidth) * qualityScaleForDpi(dedicatedDpi)));
    }

    public static int linkedHeight(int baseHeight, int dedicatedDpi) {
        return clampPx((int) Math.round(Math.max(1, baseHeight) * qualityScaleForDpi(dedicatedDpi)));
    }

    public static DpiLimits limitsForResolution(int baseW, int baseH, DisplayMetrics nativeMetrics) {
        int width = Math.max(1, baseW);
        int height = Math.max(1, baseH);

        double minScaleBySize = Math.max(
                MIN_EFFECTIVE_PX / (double) width,
                MIN_EFFECTIVE_PX / (double) height);
        double maxScaleBySize = Math.min(
                MAX_EFFECTIVE_PX / (double) width,
                MAX_EFFECTIVE_PX / (double) height);

        double globalMinScale = Math.sqrt(MIN_DEDICATED_DPI / (double) DEFAULT_DEDICATED_DPI);
        double globalMaxScale = Math.sqrt(MAX_DEDICATED_DPI / (double) DEFAULT_DEDICATED_DPI);

        double minScale = Math.max(globalMinScale, minScaleBySize);
        double maxScale = Math.min(globalMaxScale, maxScaleBySize);
        if (maxScale < minScale) maxScale = minScale;

        int minDpi = (int) Math.ceil(DEFAULT_DEDICATED_DPI * minScale * minScale);
        int maxDpi = (int) Math.floor(DEFAULT_DEDICATED_DPI * maxScale * maxScale);
        minDpi = Math.max(MIN_DEDICATED_DPI, Math.min(MAX_DEDICATED_DPI, minDpi));
        maxDpi = Math.max(minDpi, Math.min(MAX_DEDICATED_DPI, maxDpi));

        int recommended = Math.max(minDpi, Math.min(maxDpi, DEFAULT_DEDICATED_DPI));
        int targetW = linkedWidth(width, recommended);
        int targetH = linkedHeight(height, recommended);
        Candidate candidate = bestResolutionCandidate(candidates(
                Math.max(1, nativeMetrics.widthPixels),
                Math.max(1, nativeMetrics.heightPixels),
                targetW, targetH));

        return new DpiLimits(
                minDpi,
                maxDpi,
                recommended,
                candidate.width,
                candidate.height,
                candidate.factorPercent,
                candidate.inverse,
                recommended
        );
    }

    public static Plan build(ProfileStore.Profile profile, DisplayMetrics nativeMetrics) {
        int nativeW = Math.max(1, nativeMetrics.widthPixels);
        int nativeH = Math.max(1, nativeMetrics.heightPixels);

        DpiLimits limits = limitsForResolution(profile.width, profile.height, nativeMetrics);
        int dedicatedDpi = limits.clamp(profile.density);
        double qualityScale = qualityScaleForDpi(dedicatedDpi);
        int linkedTargetW = linkedWidth(profile.width, dedicatedDpi);
        int linkedTargetH = linkedHeight(profile.height, dedicatedDpi);

        Candidate resolution = bestResolutionCandidate(candidates(
                nativeW, nativeH, linkedTargetW, linkedTargetH));

        if (!profile.enabled) {
            String summary = "perfil desativado • resolução e DPI restauradas";
            return new Plan(false, false, 100,
                    nativeW, nativeH,
                    DEFAULT_DEDICATED_DPI, DEFAULT_DEDICATED_DPI,
                    limits.minDpi, limits.maxDpi,
                    profile.density, dedicatedDpi,
                    resetCommand(profile.packageName), summary);
        }

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

        String dpiAdjustment = profile.density == dedicatedDpi
                ? "DPI dedicada " + dedicatedDpi
                : "DPI dedicada " + profile.density + " → limitada a " + dedicatedDpi;

        String summary = String.format(Locale.US,
                "base %d×%d @ %d DPI • qualidade %.2fx • alvo vinculado %d×%d • efetivo aprox. %d×%d • escala Android %s%d%%",
                profile.width,
                profile.height,
                dedicatedDpi,
                qualityScale,
                linkedTargetW,
                linkedTargetH,
                resolution.width,
                resolution.height,
                resolution.inverse ? "1/" : "",
                resolution.factorPercent);

        return new Plan(true, resolution.inverse, resolution.factorPercent,
                resolution.width, resolution.height,
                dedicatedDpi, dedicatedDpi,
                limits.minDpi, limits.maxDpi,
                profile.density, dedicatedDpi,
                resolutionCommand,
                dpiAdjustment + " • " + summary);
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

    private static List<Candidate> candidates(int nativeW, int nativeH, int targetW, int targetH) {
        List<Candidate> result = new ArrayList<>();
        result.add(candidate(false, 100, 1.0, nativeW, nativeH, targetW, targetH));
        for (int factor : FACTORS) {
            double down = factor / 100.0;
            result.add(candidate(false, factor, down, nativeW, nativeH, targetW, targetH));
            double inverse = 100.0 / factor;
            result.add(candidate(true, factor, inverse, nativeW, nativeH, targetW, targetH));
        }
        return result;
    }

    private static Candidate candidate(boolean inverse, int factor, double scale,
                                       int nativeW, int nativeH,
                                       int targetW, int targetH) {
        int width = clampPx((int) Math.round(nativeW * scale));
        int height = clampPx((int) Math.round(nativeH * scale));
        return new Candidate(inverse, factor, scale, width, height,
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

    private static int clampPx(int value) {
        return Math.max(MIN_EFFECTIVE_PX, Math.min(MAX_EFFECTIVE_PX, value));
    }
}
