package com.leo.optimazer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Touch Engine privilegiado executado dentro do UserService do Shizuku.
 *
 * O núcleo nunca injeta uma segunda sequência de toques por cima do toque físico.
 * Resposta rápida usa recursos reais da plataforma/OEM quando disponíveis.
 * Arrasto linear usa o HAL touchfeature da Xiaomi quando acessível ao UID shell/root;
 * em aparelhos sem esse recurso, o resultado informa explicitamente que a camada
 * de suavização de hardware não está disponível.
 */
final class TouchEngineController {
    private static String activePackage;
    private static String originalMinRefresh;
    private static String originalPeakRefresh;
    private static boolean refreshCaptured;

    private TouchEngineController() {}

    static synchronized String apply(String packageName, boolean fastTouch,
                                     boolean linearDrag, int requestedLevel) {
        int level = Math.max(1, Math.min(100, requestedLevel));
        if (!fastTouch && !linearDrag) return reset(packageName);

        StringBuilder out = new StringBuilder();
        activePackage = packageName;

        if (fastTouch) {
            append(out, "GAME_MODE=" + runAllowFailure("cmd game mode performance " + packageName));
            append(out, applyRefreshLock());
        } else {
            append(out, "GAME_MODE=normal");
        }

        String vendor = XiaomiTouch.apply(fastTouch, linearDrag, level);
        append(out, vendor);

        if (linearDrag && !vendor.contains("XIAOMI_TOUCH_OK")) {
            append(out, "LINEAR_DRAG=LIMITED_NO_RAW_INTERCEPT");
        } else if (linearDrag) {
            append(out, "LINEAR_DRAG=OEM_SMOOTHING_ACTIVE");
        } else {
            append(out, "LINEAR_DRAG=OFF");
        }

        append(out, "FAST_TOUCH=" + (fastTouch ? "ON" : "OFF"));
        append(out, "LEVEL=" + level);
        return out.toString();
    }

    static synchronized String reset(String packageName) {
        StringBuilder out = new StringBuilder();
        append(out, "GAME_MODE=" + runAllowFailure("cmd game mode standard " + packageName));
        append(out, restoreRefreshLock());
        append(out, XiaomiTouch.reset());
        if (packageName.equals(activePackage)) activePackage = null;
        append(out, "TOUCH_ENGINE=RESTORED");
        return out.toString();
    }

    private static String applyRefreshLock() {
        if (!refreshCaptured) {
            originalMinRefresh = shellValue("settings get system min_refresh_rate");
            originalPeakRefresh = shellValue("settings get system peak_refresh_rate");
            refreshCaptured = true;
        }

        Float peak = parsePositiveFloat(originalPeakRefresh);
        if (peak == null || peak < 60f) {
            return "REFRESH_LOCK=UNCHANGED";
        }

        String a = runAllowFailure("settings put system peak_refresh_rate " + trimFloat(peak));
        String b = runAllowFailure("settings put system min_refresh_rate " + trimFloat(peak));
        return "REFRESH_LOCK=" + trimFloat(peak) + "Hz peak:" + a + " min:" + b;
    }

    private static String restoreRefreshLock() {
        if (!refreshCaptured) return "REFRESH_LOCK=NO_BASELINE";
        String a = restoreSetting("peak_refresh_rate", originalPeakRefresh);
        String b = restoreSetting("min_refresh_rate", originalMinRefresh);
        refreshCaptured = false;
        originalPeakRefresh = null;
        originalMinRefresh = null;
        return "REFRESH_RESTORE=peak:" + a + " min:" + b;
    }

    private static String restoreSetting(String key, String value) {
        if (value == null || value.isEmpty() || "null".equalsIgnoreCase(value)) {
            return runAllowFailure("settings delete system " + key);
        }
        return runAllowFailure("settings put system " + key + " " + value);
    }

    private static String shellValue(String command) {
        String value = runAllowFailure(command);
        if (value.startsWith("ERR:")) return "null";
        return value.trim();
    }

    private static Float parsePositiveFloat(String value) {
        try {
            float f = Float.parseFloat(value);
            return f > 0f ? f : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String trimFloat(float value) {
        if (Math.abs(value - Math.round(value)) < 0.001f) return String.valueOf(Math.round(value));
        return String.valueOf(value);
    }

    private static void append(StringBuilder out, String value) {
        if (value == null || value.trim().isEmpty()) return;
        if (out.length() > 0) out.append('\n');
        out.append(value.trim());
    }

    private static String runAllowFailure(String command) {
        try {
            Process process = new ProcessBuilder("/system/bin/sh", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream in = process.getInputStream()) {
                byte[] chunk = new byte[2048];
                int n;
                while ((n = in.read(chunk)) >= 0) buffer.write(chunk, 0, n);
            }
            int code = process.waitFor();
            String text = buffer.toString(StandardCharsets.UTF_8.name()).trim();
            if (code != 0) return "ERR:" + code + (text.isEmpty() ? "" : ":" + text);
            return text.isEmpty() ? "OK" : text.replace('\n', ' ');
        } catch (Exception e) {
            String message = e.getMessage();
            return "ERR:" + (message == null ? e.getClass().getSimpleName() : message);
        }
    }

    /** Best-effort Xiaomi/POCO Game Turbo touch HAL bridge. */
    private static final class XiaomiTouch {
        private static final String[] DESCRIPTORS = {
                "vendor.xiaomi.hw.touchfeature@1.0::ITouchFeature",
                "vendor.xiaomi.hardware.touchfeature@1.0::ITouchFeature"
        };
        private static final int TOUCH_ID = 0;
        private static final int[] MODES = {0, 1, 2, 3, 7};
        private static final Map<Integer, Integer> BASELINE = new HashMap<>();
        private static Object binder;
        private static String descriptor;

        static String apply(boolean fastTouch, boolean linearDrag, int level) {
            try {
                if (!connect()) return "XIAOMI_TOUCH_UNAVAILABLE=service_not_found";
                captureBaseline();

                // MIUI/HyperOS Game Turbo ativa os modos 0/1 ao entrar em jogo.
                setMode(0, 1);
                setMode(1, 1);

                if (fastTouch) setScaledMode(2, level);
                if (linearDrag) setScaledMode(3, level);

                // Valor observado no caminho de Game Turbo para manter o touch em estado gaming.
                try { setMode(7, 2); } catch (Throwable ignored) {}

                int cur2 = getCurrent(2);
                int cur3 = getCurrent(3);
                return "XIAOMI_TOUCH_OK mode2=" + cur2 + " mode3=" + cur3;
            } catch (Throwable t) {
                binder = null;
                descriptor = null;
                return "XIAOMI_TOUCH_UNAVAILABLE=" + safeMessage(t);
            }
        }

        static String reset() {
            if (BASELINE.isEmpty()) return "XIAOMI_TOUCH_RESET=no_baseline";
            try {
                if (!connect()) return "XIAOMI_TOUCH_RESET=service_not_found";
                for (Map.Entry<Integer, Integer> e : BASELINE.entrySet()) {
                    try { setMode(e.getKey(), e.getValue()); } catch (Throwable ignored) {}
                }
                BASELINE.clear();
                return "XIAOMI_TOUCH_RESET=OK";
            } catch (Throwable t) {
                return "XIAOMI_TOUCH_RESET=" + safeMessage(t);
            }
        }

        private static void captureBaseline() throws Exception {
            if (!BASELINE.isEmpty()) return;
            for (int mode : MODES) {
                try { BASELINE.put(mode, getCurrent(mode)); } catch (Throwable ignored) {}
            }
        }

        private static void setScaledMode(int mode, int level) throws Exception {
            int min = getMin(mode);
            int max = getMax(mode);
            if (max < min) {
                int t = min; min = max; max = t;
            }
            int value = min + Math.round((max - min) * (level / 100f));
            setMode(mode, value);
        }

        private static boolean connect() throws Exception {
            if (binder != null && descriptor != null) return true;
            Class<?> hwBinder = Class.forName("android.os.HwBinder");
            for (String candidate : DESCRIPTORS) {
                Object service = null;
                try {
                    Method get = hwBinder.getDeclaredMethod("getService", String.class, String.class);
                    get.setAccessible(true);
                    service = get.invoke(null, candidate, "default");
                } catch (NoSuchMethodException ignored) {
                    Method get = hwBinder.getDeclaredMethod("getService", String.class, String.class, boolean.class);
                    get.setAccessible(true);
                    service = get.invoke(null, candidate, "default", false);
                } catch (Throwable ignored) {}
                if (service != null) {
                    binder = service;
                    descriptor = candidate;
                    return true;
                }
            }
            return false;
        }

        private static int getCurrent(int mode) throws Exception { return transactRead(1, mode); }
        private static int getMax(int mode) throws Exception { return transactRead(3, mode); }
        private static int getMin(int mode) throws Exception { return transactRead(4, mode); }

        private static int transactRead(int code, int mode) throws Exception {
            Object request = newParcel();
            Object reply = newParcel();
            try {
                parcelMethod(request, "writeInterfaceToken", String.class).invoke(request, descriptor);
                parcelMethod(request, "writeInt32", int.class).invoke(request, TOUCH_ID);
                parcelMethod(request, "writeInt32", int.class).invoke(request, mode);
                transact(code, request, reply);
                verify(reply, request);
                return (Integer) parcelMethod(reply, "readInt32").invoke(reply);
            } finally {
                releaseParcel(request);
                releaseParcel(reply);
            }
        }

        private static void setMode(int mode, int value) throws Exception {
            Object request = newParcel();
            Object reply = newParcel();
            try {
                parcelMethod(request, "writeInterfaceToken", String.class).invoke(request, descriptor);
                parcelMethod(request, "writeInt32", int.class).invoke(request, TOUCH_ID);
                parcelMethod(request, "writeInt32", int.class).invoke(request, mode);
                parcelMethod(request, "writeInt32", int.class).invoke(request, value);
                transact(8, request, reply);
                verify(reply, request);
                parcelMethod(reply, "readInt32").invoke(reply);
            } finally {
                releaseParcel(request);
                releaseParcel(reply);
            }
        }

        private static Object newParcel() throws Exception {
            Class<?> clazz = Class.forName("android.os.HwParcel");
            return clazz.getDeclaredConstructor().newInstance();
        }

        private static Method parcelMethod(Object parcel, String name, Class<?>... types) throws Exception {
            Method m = parcel.getClass().getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m;
        }

        private static void transact(int code, Object request, Object reply) throws Exception {
            Method target = null;
            for (Method m : binder.getClass().getMethods()) {
                if ("transact".equals(m.getName()) && m.getParameterTypes().length == 4) {
                    target = m;
                    break;
                }
            }
            if (target == null) throw new NoSuchMethodException("HwBinder.transact");
            target.setAccessible(true);
            target.invoke(binder, code, request, reply, 0);
        }

        private static void verify(Object reply, Object request) throws Exception {
            try { parcelMethod(reply, "verifySuccess").invoke(reply); } catch (NoSuchMethodException ignored) {}
            try { parcelMethod(request, "releaseTemporaryStorage").invoke(request); } catch (NoSuchMethodException ignored) {}
        }

        private static void releaseParcel(Object parcel) {
            try { parcelMethod(parcel, "release").invoke(parcel); } catch (Throwable ignored) {}
        }

        private static String safeMessage(Throwable t) {
            Throwable c = t;
            while (c.getCause() != null && c.getCause() != c) c = c.getCause();
            String m = c.getMessage();
            return m == null || m.trim().isEmpty() ? c.getClass().getSimpleName() : m.replace('\n', ' ');
        }
    }
}
