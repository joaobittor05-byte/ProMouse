package com.leo.optimazer.bridge;

import android.net.Credentials;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.util.Base64;

import com.leo.optimazer.BridgeClient;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShellBridge {
    private static final Pattern PACKAGE_IN_ACTIVITY = Pattern.compile("([a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+)/");
    private static int expectedUid = -1;

    private ShellBridge() {}

    public static void main(String[] args) {
        try {
            expectedUid = resolvePackageUid("com.leo.optimazer");
            if (expectedUid < 10000) return;

            LocalServerSocket server = new LocalServerSocket(BridgeClient.SOCKET_NAME);
            while (true) {
                LocalSocket socket = server.accept();
                try {
                    Credentials credentials = socket.getPeerCredentials();
                    if (credentials == null || credentials.getUid() != expectedUid) {
                        writeError(socket, "UID não autorizado");
                        socket.close();
                        continue;
                    }

                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    String command = reader.readLine();
                    String response = handle(command == null ? "" : command.trim());
                    writeOk(socket, response);
                } catch (Throwable t) {
                    try { writeError(socket, t.getMessage() == null ? t.toString() : t.getMessage()); } catch (Throwable ignored) {}
                } finally {
                    try { socket.close(); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static String handle(String raw) throws Exception {
        String[] parts = raw.split("\\s+");
        if (parts.length == 0) throw new IllegalArgumentException("Comando vazio");
        String op = parts[0].toUpperCase(Locale.ROOT);

        switch (op) {
            case "PING":
                return "pong";
            case "TOP":
                return topPackage();
            case "GET_SIZE":
                return exec("wm", "size");
            case "GET_DENSITY":
                return exec("wm", "density");
            case "SET_SIZE": {
                if (parts.length != 3) throw new IllegalArgumentException("SET_SIZE width height");
                int w = parseRange(parts[1], 320, 4320, "width");
                int h = parseRange(parts[2], 320, 7680, "height");
                return exec("wm", "size", w + "x" + h);
            }
            case "SET_DENSITY": {
                if (parts.length != 2) throw new IllegalArgumentException("SET_DENSITY density");
                int d = parseRange(parts[1], 120, 1000, "density");
                return exec("wm", "density", String.valueOf(d));
            }
            case "RESET_SIZE":
                return exec("wm", "size", "reset");
            case "RESET_DENSITY":
                return exec("wm", "density", "reset");
            case "KILL_CACHED":
                return exec("am", "kill-all");
            default:
                throw new SecurityException("Operação não permitida: " + op);
        }
    }

    private static int parseRange(String value, int min, int max, String name) {
        int parsed = Integer.parseInt(value);
        if (parsed < min || parsed > max) throw new IllegalArgumentException(name + " fora do limite");
        return parsed;
    }

    private static int resolvePackageUid(String packageName) throws Exception {
        String out = exec("cmd", "package", "list", "packages", "-U", packageName);
        Matcher m = Pattern.compile("uid:(\\d+)").matcher(out);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static String topPackage() throws Exception {
        String out = exec("dumpsys", "activity", "activities");
        String[] lines = out.split("\\n");
        for (String line : lines) {
            if (line.contains("mResumedActivity") || line.contains("topResumedActivity") || line.contains("ResumedActivity")) {
                Matcher m = PACKAGE_IN_ACTIVITY.matcher(line);
                if (m.find()) return m.group(1);
            }
        }
        Matcher fallback = PACKAGE_IN_ACTIVITY.matcher(out);
        return fallback.find() ? fallback.group(1) : "";
    }

    private static String exec(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = process.getInputStream()) {
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) >= 0) buffer.write(chunk, 0, n);
        }
        int code = process.waitFor();
        String out = buffer.toString(StandardCharsets.UTF_8.name()).trim();
        if (code != 0) throw new IllegalStateException(command[0] + " retornou " + code + ": " + out);
        return out;
    }

    private static void writeOk(LocalSocket socket, String text) throws Exception {
        String encoded = Base64.encodeToString((text == null ? "" : text).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        writer.write("OK\t" + encoded + "\n");
        writer.flush();
    }

    private static void writeError(LocalSocket socket, String text) throws Exception {
        String encoded = Base64.encodeToString((text == null ? "erro" : text).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        writer.write("ERR\t" + encoded + "\n");
        writer.flush();
    }
}
