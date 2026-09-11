package com.leo.optimazer;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fallback Shizuku Shell para ROMs onde UserService não inicia. */
final class LeoFallbackDispatcher {
    private static final Pattern PACKAGE = Pattern.compile("[a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+");
    private static final Pattern TOP_ACTIVITY = Pattern.compile("(?m)(?:mResumedActivity|topResumedActivity|ResumedActivity)[^\\n]*?\\s([a-zA-Z0-9_.]+)/(?:[a-zA-Z0-9_.$]+)");
    private static final Pattern CURRENT_FOCUS = Pattern.compile("(?m)mCurrentFocus[^\\n]*?\\s([a-zA-Z0-9_.]+)/(?:[a-zA-Z0-9_.$]+)");

    private LeoFallbackDispatcher() {}

    static String execute(String command) {
        if (command == null || command.trim().isEmpty()) throw new IllegalArgumentException("Comando vazio");
        StringBuilder out = new StringBuilder();
        for (String segment : command.split(";")) {
            String clean = segment.trim();
            if (clean.isEmpty()) continue;
            String result = dispatch(clean);
            if (result != null && !result.trim().isEmpty()) {
                if (out.length() > 0) out.append('\n');
                out.append(result.trim());
            }
        }
        return out.toString();
    }

    private static String dispatch(String command) {
        String[] p = command.trim().split("\\s+");
        if (p.length >= 2 && "leo".equals(p[0])) {
            switch (p[1]) {
                case "top":
                    requireLength(p, 2); return topPackage();
                case "frame-apply":
                    if (p.length != 4 && p.length != 5) throw new SecurityException("Formato frame-apply inválido");
                    requirePackage(p[2]);
                    return FrameMatchController.apply(p[2], parseFps(p[3]), p.length == 5 ? validMode(p[4]) : FrameRepeatPrefs.MODE_COMPETITIVE);
                case "frame-status":
                    requireLength(p, 3); requirePackage(p[2]); return FrameMatchController.status(p[2]);
                case "frame-reset":
                    requireLength(p, 3); requirePackage(p[2]); return FrameMatchController.reset(p[2]);
                case "touch-apply":
                    requireLength(p, 7); requirePackage(p[2]);
                    int level = Integer.parseInt(p[5]);
                    if (level < 1 || level > 100) throw new SecurityException("Touch fora do limite");
                    return FallbackTouchController.apply(p[2], "1".equals(p[3]), "1".equals(p[4]), level);
                case "touch-reset":
                    requireLength(p, 3); requirePackage(p[2]); return FallbackTouchController.reset(p[2]);
                case "density":
                    requireLength(p, 4); requirePackage(p[2]);
                    return "TASK_DENSITY_REJECTED " + p[2] + " reason=USER_SERVICE_UNAVAILABLE fallback=SHIZUKU_SHELL resolution_compat=AVAILABLE";
                case "density-reset":
                    requireLength(p, 3); requirePackage(p[2]); return "TASK_DENSITY_RESET " + p[2] + " fallback=SHIZUKU_SHELL no_task_override=true";
                case "density-status":
                    requireLength(p, 3); requirePackage(p[2]); return "TASK_DENSITY_STATUS " + p[2] + " fallback=SHIZUKU_SHELL verified=false";
                default: throw new SecurityException("Operação Leo não permitida no fallback");
            }
        }
        validateShell(command, p);
        return PrivilegedShell.runStrict(command);
    }

    private static void validateShell(String command, String[] p) {
        if (p.length == 2 && "am".equals(p[0]) && "kill-all".equals(p[1])) return;
        if (p.length == 3 && "am".equals(p[0]) && "force-stop".equals(p[1])) { requirePackage(p[2]); return; }
        if (p.length == 5 && "am".equals(p[0]) && "compat".equals(p[1])) {
            String action = p[2].toLowerCase(Locale.ROOT);
            if (!"enable".equals(action) && !"reset".equals(action)) throw new SecurityException("Ação compat não permitida");
            String change = p[3];
            if (!("DOWNSCALED".equals(change) || "DOWNSCALED_INVERSE".equals(change) || change.matches("DOWNSCALE_(30|35|40|45|50|55|60|65|70|75|80|85|90)"))) throw new SecurityException("Compat change não permitido");
            requirePackage(p[4]); return;
        }
        throw new SecurityException("Shell não permitido no fallback: " + command);
    }

    private static String topPackage() {
        String activities = PrivilegedShell.runAllowFailure("dumpsys activity activities");
        String found = findPackage(activities, TOP_ACTIVITY);
        if (!found.isEmpty()) return found;
        return findPackage(PrivilegedShell.runAllowFailure("dumpsys window windows"), CURRENT_FOCUS);
    }
    private static String findPackage(String text, Pattern pattern) {
        if (text == null || text.startsWith("ERR:")) return "";
        Matcher matcher = pattern.matcher(text); return matcher.find() ? matcher.group(1) : "";
    }
    private static int parseFps(String value) {
        int fps = Integer.parseInt(value); if (fps == 0) return 0;
        if (fps < 20 || fps > 240) throw new SecurityException("FPS fora do limite"); return fps;
    }
    private static String validMode(String value) {
        String mode = FrameRepeatPrefs.normalizeMode(value);
        if (!mode.equals(value)) throw new SecurityException("Modo inválido");
        return mode;
    }
    private static void requirePackage(String value) { if (!PACKAGE.matcher(value).matches()) throw new SecurityException("Pacote inválido"); }
    private static void requireLength(String[] p, int length) { if (p.length != length) throw new SecurityException("Formato de comando inválido"); }
}
