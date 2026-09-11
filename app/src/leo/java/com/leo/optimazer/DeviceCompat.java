package com.leo.optimazer;

import android.os.Build;

import java.util.Locale;

/**
 * Compatibilidade orientada por capacidade, não por fabricante.
 * O Leo não depende de APIs de GPU específicas de Qualcomm/MediaTek/etc.
 */
final class DeviceCompat {
    private DeviceCompat() {}

    static String chipFamily() {
        String haystack = ((Build.HARDWARE == null ? "" : Build.HARDWARE) + " "
                + (Build.BOARD == null ? "" : Build.BOARD) + " "
                + (Build.DEVICE == null ? "" : Build.DEVICE) + " "
                + (Build.MANUFACTURER == null ? "" : Build.MANUFACTURER)).toLowerCase(Locale.ROOT);

        if (Build.VERSION.SDK_INT >= 31 && Build.SOC_MANUFACTURER != null) {
            haystack += " " + Build.SOC_MANUFACTURER.toLowerCase(Locale.ROOT);
        }
        if (Build.VERSION.SDK_INT >= 31 && Build.SOC_MODEL != null) {
            haystack += " " + Build.SOC_MODEL.toLowerCase(Locale.ROOT);
        }

        if (haystack.contains("qualcomm") || haystack.contains("qcom") || haystack.contains("sm")) return "Snapdragon / Qualcomm";
        if (haystack.contains("mediatek") || haystack.contains("mtk") || haystack.contains("dimensity") || haystack.contains("helio")) return "MediaTek";
        if (haystack.contains("exynos") || haystack.contains("samsung")) return "Exynos / Samsung";
        if (haystack.contains("tensor") || haystack.contains("gs101") || haystack.contains("gs201")) return "Google Tensor";
        if (haystack.contains("unisoc") || haystack.contains("spreadtrum") || haystack.contains("ums")) return "Unisoc";
        if (haystack.contains("kirin") || haystack.contains("hisilicon")) return "Kirin / HiSilicon";
        if (haystack.contains("rockchip")) return "Rockchip";
        return "Outro / genérico";
    }

    static String summary() {
        String socModel = "";
        if (Build.VERSION.SDK_INT >= 31 && Build.SOC_MODEL != null && !Build.SOC_MODEL.trim().isEmpty()) {
            socModel = " • " + Build.SOC_MODEL.trim();
        }
        return "Compatibilidade universal por capacidade\n"
                + "SoC: " + chipFamily() + socModel + "\n"
                + "Android " + Build.VERSION.RELEASE + " • API " + Build.VERSION.SDK_INT + "\n"
                + "Backend: UserService quando suportado, Shell Compat como fallback";
    }
}
