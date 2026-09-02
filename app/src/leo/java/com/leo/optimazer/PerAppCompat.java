package com.leo.optimazer;

import android.util.DisplayMetrics;

import java.util.Locale;

public final class PerAppCompat {
    private static final int[] FACTORS = {30,35,40,45,50,55,60,65,70,75,80,85,90};

    public static final class Plan {
        public final boolean enabled;
        public final boolean inverse;
        public final int factorPercent;
        public final int estimatedWidth;
        public final int estimatedHeight;
        public final int estimatedDensity;
        public final String command;
        public final String summary;

        Plan(boolean enabled, boolean inverse, int factorPercent,
             int estimatedWidth, int estimatedHeight, int estimatedDensity,
             String command, String summary) {
            this.enabled = enabled;
            this.inverse = inverse;
            this.factorPercent = factorPercent;
            this.estimatedWidth = estimatedWidth;
            this.estimatedHeight = estimatedHeight;
            this.estimatedDensity = estimatedDensity;
            this.command = command;
            this.summary = summary;
        }
    }

    private PerAppCompat() {}

    public static Plan build(ProfileStore.Profile profile, DisplayMetrics nativeMetrics) {
        int nativeW = Math.max(1, nativeMetrics.widthPixels);
        int nativeH = Math.max(1, nativeMetrics.heightPixels);
        int nativeDpi = Math.max(1, nativeMetrics.densityDpi);

        if (!profile.enabled) {
            String reset = resetCommand(profile.packageName);
            return new Plan(false, false, 100, nativeW, nativeH, nativeDpi, reset,
                    "Perfil desativado • restaura escala padrão do aplicativo");
        }

        double densityRatio = profile.density / (double) nativeDpi;
        double widthRatio = profile.width / (double) nativeW;
        double heightRatio = profile.height / (double) nativeH;
        double resolutionRatio = Math.min(widthRatio, heightRatio);

        boolean densityChanged = Math.abs(densityRatio - 1.0) >= 0.04;
        boolean inverse;
        double rawFactor;
        String priority;

        if (densityChanged) {
            inverse = densityRatio > 1.0;
            rawFactor = inverse ? 1.0 / densityRatio : densityRatio;
            priority = "DPI priorizada";
        } else {
            inverse = resolutionRatio > 1.0;
            rawFactor = inverse ? 1.0 / resolutionRatio : resolutionRatio;
            priority = "resolução priorizada";
        }

        if (rawFactor >= 0.95 && rawFactor <= 1.05) {
            String reset = resetCommand(profile.packageName);
            return new Plan(true, false, 100, nativeW, nativeH, nativeDpi, reset,
                    "Escala individual 100% • sem alteração necessária");
        }

        int factor = nearestFactor(rawFactor * 100.0);
        double appliedScale = inverse ? (100.0 / factor) : (factor / 100.0);
        int estimatedW = (int) Math.round(nativeW * appliedScale);
        int estimatedH = (int) Math.round(nativeH * appliedScale);
        int estimatedDpi = (int) Math.round(nativeDpi * appliedScale);

        StringBuilder command = new StringBuilder();
        command.append(resetCommand(profile.packageName)).append("; ");
        command.append("am compat enable ")
                .append(inverse ? "DOWNSCALED_INVERSE" : "DOWNSCALED")
                .append(' ').append(profile.packageName).append("; ");
        command.append("am compat enable DOWNSCALE_").append(factor)
                .append(' ').append(profile.packageName).append("; ");
        command.append("am force-stop ").append(profile.packageName);

        String summary = String.format(Locale.US,
                "%s • escala %s%d%% • efetivo aprox. %d×%d • %d DPI",
                priority,
                inverse ? "1/" : "",
                factor,
                estimatedW,
                estimatedH,
                estimatedDpi);

        return new Plan(true, inverse, factor, estimatedW, estimatedH, estimatedDpi,
                command.toString(), summary);
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

    private static int nearestFactor(double wanted) {
        int best = FACTORS[0];
        double bestDistance = Math.abs(wanted - best);
        for (int factor : FACTORS) {
            double distance = Math.abs(wanted - factor);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = factor;
            }
        }
        return best;
    }
}
