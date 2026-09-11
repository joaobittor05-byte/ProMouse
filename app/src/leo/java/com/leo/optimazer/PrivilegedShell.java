package com.leo.optimazer;

import android.os.Process;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import rikka.shizuku.Shizuku;

/**
 * Executa comandos com o melhor backend disponível.
 *
 * Dentro do UserService, o processo já é shell/root e usamos ProcessBuilder normal.
 * No processo normal do app, usamos Shizuku.newProcess por reflexão como fallback
 * compatível para ROMs Xiaomi/MediaTek onde UserService não inicia.
 */
final class PrivilegedShell {
    private PrivilegedShell() {}

    static boolean canUseShizukuProcess() {
        try {
            if (!Shizuku.pingBinder()) return false;
            Method method = Shizuku.class.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            method.setAccessible(true);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static String runAllowFailure(String command) {
        try {
            return run(command, false);
        } catch (Throwable t) {
            String message = safeMessage(t);
            return "ERR:" + message.replace('\n', ' ');
        }
    }

    static String runStrict(String command) {
        try {
            String result = run(command, true);
            if (result.startsWith("ERR:")) throw new IllegalStateException(result.substring(4));
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(safeMessage(t), t);
        }
    }

    private static String run(String command, boolean strict) throws Exception {
        int uid = Process.myUid();
        java.lang.Process process;

        if (uid == 0 || uid == 2000) {
            process = new ProcessBuilder("/system/bin/sh", "-c", command)
                    .redirectErrorStream(true)
                    .start();
        } else {
            if (!ShizukuCore.hasPermission()) {
                throw new IllegalStateException("Permissão do Shizuku não concedida");
            }
            process = newRemoteProcess(command);
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = process.getInputStream()) {
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) >= 0) buffer.write(chunk, 0, n);
        }
        int code = process.waitFor();
        String text = buffer.toString(StandardCharsets.UTF_8.name()).trim();
        if (code != 0) {
            String error = "exit=" + code + (text.isEmpty() ? "" : ":" + text.replace('\n', ' '));
            if (strict) throw new IllegalStateException(error);
            return "ERR:" + error;
        }
        return text.isEmpty() ? "OK" : text;
    }

    private static java.lang.Process newRemoteProcess(String command) throws Exception {
        try {
            Method method = Shizuku.class.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            method.setAccessible(true);
            Object result = method.invoke(null,
                    new Object[]{new String[]{"/system/bin/sh", "-c", command + " 2>&1"}, null, null});
            if (!(result instanceof java.lang.Process)) {
                throw new IllegalStateException("Backend Shizuku não retornou Process");
            }
            return (java.lang.Process) result;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException(safeMessage(cause), cause);
        }
    }

    private static String safeMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.trim().isEmpty()
                ? cause.getClass().getSimpleName() : message;
    }
}
