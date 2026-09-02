package com.leo.optimazer;

import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PerAppCompat {
    public static final int VIRTUAL_DPI_BASE = 800;
    private static final int[] FACTORS = {30,35,40,45,50,55,60,65,70,75,80,85,90};
    private static final double RESOLUTION_TOLERANCE = 0.065;

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
            if (minDpi == maxDpi) {
                return "DPI virtual compatível nesta resolução: " + minDpi;
            }
            return "DPI virtual compatível nesta resolução: " + minDpi + "–" + maxDpi;
        }
    }

    public static final class Plan {
        public final boolean enabled;
        public final boolean inverse;
        public final int factorPercent;
        public final int estimatedWidth;
        public final int estimatedHeight;
        public final int estimatedDensity;
        public final int estimatedVirtualDpi;
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
        final int androidDensity;
        final int virtualDpi;
        final double resolutionError;

        Candidate(boolean inverse, int factorPercent, double scale,
                  int width, int height, int androidDensity, int virtualDpi,
                  double resolutionError) {
            this.inverse = inverse;
            this.factorPercent = factorPercent;
            this.scale = scale;
            this.width = width;
            this.height = height;
            this.androidDensity = androidDensity;
            this.virtualDpi = virtualDpi;
            this.resolutionError = resolutionError;
        }
    }

    private PerAppCompat() {}

    public static int androidDensityFromVirtual(int virtualDpi, int nativeAndroidDensity) {
        int safeVirtual = Math.max(1, virtualDpi);
        return Math.max(1, (int) Math.round(nativeAndroidDensity * (VIRTUAL_DPI_BASE / (double) safeVirtual)));
    }

    public static int virtualFromAndroidDensity(int androidDensity, int nativeAndroidDensity) {
        int safeAndroid = Math.max(1, androidDensity);
        return Math.max(1, (int) Math.round(VIRTUAL_DPI_BASE * (nativeAndroidDensity / (double) safeAndroid)));
    }

    public static DpiLimits limitsForResolution(int requestedW, int requestedH, DisplayMetrics nativeMetrics) {
        int nativeW = Math.max(1, nativeMetrics.widthPixels);
        int nativeH = Math.max(1, nativeMetrics.heightPixels);
        int nativeDpi = Math.max(1, nativeMetrics.densityDpi);
        int targetW = Math.max(1, requestedW);
        int targetH = Math.max(1, requestedH);

        List<Candidate> all = candidates(nativeW, nativeH, nativeDpi, targetW, targetH);
        Candidate best = bestResolutionCandidate(all);
        double threshold = Math.max(RESOLUTION_TOLERANCE, best.resolutionError + 0.025);

        int minVirtual = Integer.MAX_VALUE;
        int maxVirtual = Integer.MIN_VALUE;
        for (Candidate candidate : all) {
            if (candidate.resolutionError <= threshold) {
                minVirtual = Math.min(minVirtual, candidate.virtualDpi);
                maxVirtual = Math.max(maxVirtual, candidate.virtualDpi);
            }
        }

        if (minVirtual == Integer.MAX_VALUE) {
            minVirtual = best.virtualDpi;
            maxVirtual = best.virtualDpi;
        }

        minVirtual = Math.max(100, minVirtual);
        maxVirtual = Math.min(4000, maxVirtual);
        if (maxVirtual < minVirtual) maxVirtual = minVirtual;

        int recommended = Math.max(minVirtual, Math.min(maxVirtual, best.virtualDpi));
        return new DpiLimits(
                minVirtual,
                maxVirtual,
                recommended,
                best.width,
                best.height,
                best.factorPercent,
                best.inverse,
                best.androidDensity
        );
    }

    public static Plan build(ProfileStore.Profile profile, DisplayMetrics nativeMetrics) {
        int nativeW = Math.max(1, nativeMetrics.widthPixels);
        int nativeH = Math.max(1, nativeMetrics.heightPixels);
        int nativeDpi = Math.max(1, nativeMetrics.densityDpi);

        DpiLimits limits = limitsForResolution(profile.width, profile.height, nativeMetrics);
        int normalizedVirtualDpi = limits.clamp(profile.density);

        if (!profile.enabled) {
            String reset = resetCommand(profile.packageName);
            String summary = limits.label() + " • perfil desativado • padrão do Android";
            return new Plan(false, false, 100,
                    nativeW, nativeH,
                    nativeDpi, VIRTUAL_DPI_BASE,
                    limits.minDpi, limits.maxDpi,
                    profile.density, normalizedVirtualDpi,
                    reset, summary);
        }

        List<Candidate> all = candidates(nativeW, nativeH, nativeDpi, profile.width, profile.height);
        Candidate bestResolution = bestResolutionCandidate(all);
        double threshold = Math.max(RESOLUTION_TOLERANCE, bestResolution.resolutionError + 0.025);

        Candidate selected = null;
        long bestVirtualDistance = Long.MAX_VALUE;
        double bestResolutionError = Double.MAX_VALUE;

        for (Candidate candidate : all) {
            if (candidate.resolutionError > threshold) continue;
            long virtualDistance = Math.abs((long) candidate.virtualDpi - normalizedVirtualDpi);
            if (selected == null || virtualDistance < bestVirtualDistance ||
                    (virtualDistance == bestVirtualDistance && candidate.resolutionError < bestResolutionError)) {
                selected = candidate;
                bestVirtualDistance = virtualDistance;
                bestResolutionError = candidate.resolutionError;
            }
        }

        if (selected == null) selected = bestResolution;

        String command;
        if (Math.abs(selected.scale - 1.0) < 0.01) {
            command = resetCommand(profile.packageName);
        } else {
            StringBuilder builder = new StringBuilder();
            builder.append(resetCommand(profile.packageName)).append("; ");
            builder.append("am compat enable ")
                    .append(selected.inverse ? "DOWNSCALED_INVERSE" : "DOWNSCALED")
                    .append(' ').append(profile.packageName).append("; ");
            builder.append("am compat enable DOWNSCALE_").append(selected.factorPercent)
                    .append(' ').append(profile.packageName).append("; ");
            builder.append("am force-stop ").append(profile.packageName);
            command = builder.toString();
        }

        String dpiAdjustment = profile.density == normalizedVirtualDpi
                ? "DPI virtual solicitado " + profile.density
                : "DPI virtual solicitado " + profile.density + " → limitado a " + normalizedVirtualDpi;

        String summary = String.format(Locale.US,
                "%s • %s • escala %s%d%% • efetivo aprox. %d×%d • DPI virtual %d • densidade Android ~%d",
                limits.label(),
                dpiAdjustment,
                selected.inverse ? "1/" : "",
                selected.factorPercent,
                selected.width,
                selected.height,
                selected.virtualDpi,
                selected.androidDensity);

        return new Plan(true, selected.inverse, selected.factorPercent,
                selected.width, selected.height,
                selected.androidDensity, selected.virtualDpi,
                limits.minDpi, limits.maxDpi,
                profile.density, normalizedVirtualDpi,
                command, summary);
    }

    public static String resetCommand(String packageName) {
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
        int androidDensity = Math.max(1, (int) Math.round(nativeDpi * scale));
        int virtualDpi = virtualFromAndroidDensity(androidDensity, nativeDpi);
        double error = resolutionError(width, height, targetW, targetH);
        return new Candidate(inverse, factor, scale, width, height,
                androidDensity, virtualDpi, error);
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
