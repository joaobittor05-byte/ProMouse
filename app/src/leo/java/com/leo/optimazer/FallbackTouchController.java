package com.leo.optimazer;

final class FallbackTouchController {
    private static String activePackage;

    private FallbackTouchController() {}

    static synchronized String apply(String packageName, boolean fastTouch,
                                     boolean linearDrag, int requestedLevel) {
        int level = Math.max(1, Math.min(100, requestedLevel));
        if (!fastTouch && !linearDrag) return reset(packageName);

        StringBuilder out = new StringBuilder();
        activePackage = packageName;
        append(out, "TOUCH_ENGINE=UNIVERSAL_AOSP");
        append(out, "BACKEND=SHIZUKU_SHELL_FALLBACK");

        if (fastTouch) {
            append(out, "AOSP_GAME_MODE=" + compact(
                    PrivilegedShell.runAllowFailure("cmd game mode performance " + packageName)));
            append(out, "FAST_TOUCH=AOSP_PERFORMANCE_PIPELINE");
        } else {
            append(out, "FAST_TOUCH=OFF");
        }

        if (linearDrag) {
            String noResample = shellValue("getprop ro.input.noresample");
            String legacy = shellValue("getprop ro.input.resampling");
            if ("1".equals(noResample) || "0".equals(legacy)) {
                append(out, "LINEAR_DRAG=ROM_DISABLED_AOSP_RESAMPLING ROOT_FRAMEWORK_REQUIRED");
            } else {
                String monitor = PrivilegedShell.runAllowFailure(
                        "cmd package check-permission android.permission.MONITOR_INPUT com.android.shell");
                boolean granted = monitor.toLowerCase().contains("granted");
                append(out, granted
                        ? "LINEAR_DRAG=AOSP_RESAMPLING_ACTIVE RAW_MONITOR_AVAILABLE"
                        : "LINEAR_DRAG=AOSP_RESAMPLING_ACTIVE RAW_MONITOR_UNAVAILABLE");
            }
        } else {
            append(out, "LINEAR_DRAG=OFF");
        }

        append(out, "LEVEL=" + level);
        return out.toString();
    }

    static synchronized String reset(String packageName) {
        StringBuilder out = new StringBuilder();
        append(out, "AOSP_GAME_MODE=" + compact(
                PrivilegedShell.runAllowFailure("cmd game mode standard " + packageName)));
        if (packageName.equals(activePackage)) activePackage = null;
        append(out, "TOUCH_ENGINE=RESTORED");
        append(out, "BACKEND=SHIZUKU_SHELL_FALLBACK");
        return out.toString();
    }

    private static String shellValue(String command) {
        String value = PrivilegedShell.runAllowFailure(command);
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
}
