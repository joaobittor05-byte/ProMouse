package com.leo.optimazer;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Base64;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public final class BridgeClient {
    public static final String SOCKET_NAME = "leo_optimazer_bridge_v1";

    private BridgeClient() {}

    public static String send(String command) throws Exception {
        LocalSocket socket = new LocalSocket();
        socket.connect(new LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT));
        socket.setSoTimeout(5000);

        OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        writer.write(command);
        writer.write("\n");
        writer.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String line = reader.readLine();
        socket.close();

        if (line == null) throw new IllegalStateException("Bridge sem resposta");
        if (line.startsWith("OK\t")) {
            byte[] decoded = Base64.decode(line.substring(3), Base64.DEFAULT);
            return new String(decoded, StandardCharsets.UTF_8);
        }
        if (line.equals("OK")) return "";
        if (line.startsWith("ERR\t")) {
            byte[] decoded = Base64.decode(line.substring(4), Base64.DEFAULT);
            throw new IllegalStateException(new String(decoded, StandardCharsets.UTF_8));
        }
        throw new IllegalStateException(line);
    }

    public static boolean isAlive() {
        try {
            return "pong".equals(send("PING").trim());
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String activationCommandForWindowsCmd() {
        return "for /f \"tokens=2 delims=:\" %A in ('adb shell pm path com.leo.optimazer') do adb shell \"app_process -Djava.class.path=%A /system/bin com.leo.optimazer.bridge.ShellBridge >/dev/null 2>&1 &\"";
    }
}
