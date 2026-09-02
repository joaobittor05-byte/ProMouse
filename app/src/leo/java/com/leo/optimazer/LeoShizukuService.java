package com.leo.optimazer;

import android.content.Context;

import androidx.annotation.Keep;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public class LeoShizukuService extends ILeoShell.Stub {
    private static final Pattern PACKAGE = Pattern.compile("[a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+");
    private static final Pattern SCALE = Pattern.compile("DOWNSCALE_(30|35|40|45|50|55|60|65|70|75|80|85|90)");

    public LeoShizukuService() {}

    @Keep
    public LeoShizukuService(Context context) {}

    @Override
    public String execute(String command) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Comando vazio");
        }

        String[] segments = command.split(";");
        StringBuilder out = new StringBuilder();
        for (String segment : segments) {
            String clean = segment.trim();
            if (clean.isEmpty()) continue;
            validate(clean);
            String result = dispatch(clean);
            if (!result.isEmpty()) {
                if (out.length() > 0) out.append('\n');
                out.append(result);
            }
        }
        return out.toString();
    }

    @Override
    public int getServiceUid() {
        return android.os.Process.myUid();
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    private static void validate(String command) {
        String[] p = command.trim().split("\\s+");

        if (p.length == 2 && "leo".equals(p[0]) && "top".equals(p[1])) return;

        if (p.length == 4 && "leo".equals(p[0]) && "density".equals(p[1])
                && validPackage(p[2]) && validDensity(p[3])) return;

        if (p.length == 3 && "leo".equals(p[0]) && "density-reset".equals(p[1])
                && validPackage(p[2])) return;

        if (p.length == 3 && "leo".equals(p[0]) && "density-status".equals(p[1])
                && validPackage(p[2])) return;

        if (p.length == 2 && "am".equals(p[0]) && "kill-all".equals(p[1])) return;

        if (p.length == 3 && "am".equals(p[0]) && "force-stop".equals(p[1]) && validPackage(p[2])) return;

        if (p.length == 5 && "am".equals(p[0]) && "compat".equals(p[1])) {
            String action = p[2].toLowerCase(Locale.ROOT);
            String change = p[3];
            String pkg = p[4];
            boolean allowedAction = "enable".equals(action) || "reset".equals(action);
            boolean allowedChange = "DOWNSCALED".equals(change)
                    || "DOWNSCALED_INVERSE".equals(change)
                    || SCALE.matcher(change).matches();
            if (allowedAction && allowedChange && validPackage(pkg)) return;
        }

        throw new SecurityException("Operação não permitida pelo núcleo Shizuku: " + command);
    }

    private static String dispatch(String command) {
        String[] p = command.trim().split("\\s+");
        if (p.length >= 2 && "leo".equals(p[0])) {
            switch (p[1]) {
                case "top":
                    return TaskDensityController.topPackage();
                case "density":
                    return TaskDensityController.apply(p[2], Integer.parseInt(p[3]));
                case "density-reset":
                    return TaskDensityController.reset(p[2]);
                case "density-status":
                    return TaskDensityController.status(p[2]);
                default:
                    throw new SecurityException("Operação Leo não permitida");
            }
        }
        return runShell(command);
    }

    private static boolean validPackage(String value) {
        return PACKAGE.matcher(value).matches();
    }

    private static boolean validDensity(String value) {
        try {
            int density = Integer.parseInt(value);
            return density >= 72 && density <= 1000;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String runShell(String command) {
        try {
            java.lang.Process process = new ProcessBuilder("/system/bin/sh", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream in = process.getInputStream()) {
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) >= 0) buffer.write(chunk, 0, n);
            }
            int code = process.waitFor();
            String result = buffer.toString(StandardCharsets.UTF_8.name()).trim();
            if (code != 0) {
                throw new IllegalStateException("Falha shell (" + code + "): " + result);
            }
            return result;
        } catch (Exception e) {
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            throw new IllegalStateException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
        }
    }
}
