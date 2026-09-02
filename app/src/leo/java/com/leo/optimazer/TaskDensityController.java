package com.leo.optimazer;

import android.content.ComponentName;
import android.content.res.Configuration;
import android.os.SystemClock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aplica DPI somente ao WindowContainer/Task de um aplicativo.
 *
 * A resolução em pixels/bounds NÃO é alterada. Para que o app receba uma
 * configuração coerente, o Leo altera densityDpi e recalcula apenas
 * screenWidthDp/screenHeightDp a partir do mesmo canvas em pixels.
 *
 * Exemplo: um app com canvas efetivo de 1080 px continua com 1080 px. Ao mudar
 * a densidade, muda somente quantos dp representam esses mesmos 1080 px.
 */
final class TaskDensityController {
    private static final int MIN_DENSITY = 72;
    private static final int MAX_DENSITY = 1000;

    private static final class Baseline {
        final String packageName;
        final int density;
        final int screenWidthDp;
        final int screenHeightDp;
        final int canvasWidthPx;
        final int canvasHeightPx;
        int lastAppliedDensity;
        int lastAppliedWidthDp;
        int lastAppliedHeightDp;

        Baseline(String packageName, Configuration config) {
            this.packageName = packageName;
            this.density = safeDensity(config.densityDpi);
            this.screenWidthDp = Math.max(1, config.screenWidthDp);
            this.screenHeightDp = Math.max(1, config.screenHeightDp);
            this.canvasWidthPx = Math.max(1,
                    (int) Math.round(this.screenWidthDp * this.density / 160.0));
            this.canvasHeightPx = Math.max(1,
                    (int) Math.round(this.screenHeightDp * this.density / 160.0));
            this.lastAppliedDensity = this.density;
            this.lastAppliedWidthDp = this.screenWidthDp;
            this.lastAppliedHeightDp = this.screenHeightDp;
        }
    }

    private static final Map<Integer, Baseline> BASELINES = new HashMap<>();

    private TaskDensityController() {}

    static synchronized String topPackage() {
        try {
            List<?> tasks = getTasks(32);
            Object fallback = null;
            for (Object task : tasks) {
                ComponentName top = topActivity(task);
                if (top == null) continue;
                if (fallback == null) fallback = task;
                if (booleanField(task, "isFocused", false)) return top.getPackageName();
            }
            if (fallback != null) {
                ComponentName top = topActivity(fallback);
                return top == null ? "" : top.getPackageName();
            }
            return "";
        } catch (Throwable t) {
            throw new IllegalStateException("TOP_TASK_UNAVAILABLE: " + message(t), t);
        }
    }

    static synchronized String apply(String packageName, int requestedDensity) {
        int density = safeDensity(requestedDensity);
        try {
            Object task = findTask(packageName);
            if (task == null) return "TASK_NOT_RUNNING " + packageName;

            int taskId = intField(task, "taskId", -1);
            Configuration current = taskConfiguration(task);
            Baseline baseline = BASELINES.get(taskId);
            if (baseline == null || !baseline.packageName.equals(packageName)) {
                baseline = new Baseline(packageName, current);
                BASELINES.put(taskId, baseline);
            }

            // Mantém exatamente o mesmo canvas derivado em pixels da tarefa e muda
            // somente sua interpretação em dp de acordo com a nova densidade.
            int wantedWidthDp = Math.max(1,
                    (int) Math.round(baseline.canvasWidthPx * 160.0 / density));
            int wantedHeightDp = Math.max(1,
                    (int) Math.round(baseline.canvasHeightPx * 160.0 / density));

            if (current.densityDpi == density
                    && current.screenWidthDp == wantedWidthDp
                    && current.screenHeightDp == wantedHeightDp) {
                return statusLine("TASK_DENSITY_ALREADY", packageName, taskId,
                        baseline, current, density, wantedWidthDp, wantedHeightDp, true);
            }

            applyDensityAndDpSize(task, density, wantedWidthDp, wantedHeightDp);
            SystemClock.sleep(140L);

            Configuration verified = readCurrentConfiguration(packageName);
            boolean ok = matches(verified, density, wantedWidthDp, wantedHeightDp);

            // Alguns OEMs aceitam a transação mas atualizam a TaskInfo com atraso.
            if (!ok) {
                Object retryTask = findTask(packageName);
                if (retryTask != null) {
                    applyDensityAndDpSize(retryTask, density, wantedWidthDp, wantedHeightDp);
                    SystemClock.sleep(180L);
                    verified = readCurrentConfiguration(packageName);
                    ok = matches(verified, density, wantedWidthDp, wantedHeightDp);
                }
            }

            baseline.lastAppliedDensity = density;
            baseline.lastAppliedWidthDp = wantedWidthDp;
            baseline.lastAppliedHeightDp = wantedHeightDp;

            if (!ok) {
                int actualDensity = verified == null ? -1 : verified.densityDpi;
                int actualWdp = verified == null ? -1 : verified.screenWidthDp;
                int actualHdp = verified == null ? -1 : verified.screenHeightDp;
                return "TASK_DENSITY_REJECTED " + packageName
                        + " requested=" + density
                        + " actual=" + actualDensity
                        + " requestedDp=" + wantedWidthDp + "x" + wantedHeightDp
                        + " actualDp=" + actualWdp + "x" + actualHdp
                        + " pixels=" + baseline.canvasWidthPx + "x" + baseline.canvasHeightPx;
            }

            return statusLine("TASK_DENSITY_OK", packageName, taskId,
                    baseline, verified, density, wantedWidthDp, wantedHeightDp, true);
        } catch (Throwable t) {
            throw new IllegalStateException("TASK_DENSITY_UNSUPPORTED: " + message(t), t);
        }
    }

    static synchronized String status(String packageName) {
        try {
            Object task = findTask(packageName);
            if (task == null) return "TASK_NOT_RUNNING " + packageName;
            int taskId = intField(task, "taskId", -1);
            Configuration current = taskConfiguration(task);
            Baseline baseline = BASELINES.get(taskId);
            if (baseline == null || !baseline.packageName.equals(packageName)) {
                int density = safeDensity(current.densityDpi);
                int pxW = Math.max(1, (int) Math.round(current.screenWidthDp * density / 160.0));
                int pxH = Math.max(1, (int) Math.round(current.screenHeightDp * density / 160.0));
                return "TASK_DENSITY_STATUS " + packageName
                        + " current=" + density
                        + " dp=" + current.screenWidthDp + "x" + current.screenHeightDp
                        + " pixels~=" + pxW + "x" + pxH
                        + " baseline=none task=" + taskId;
            }
            return statusLine("TASK_DENSITY_STATUS", packageName, taskId,
                    baseline, current, baseline.lastAppliedDensity,
                    baseline.lastAppliedWidthDp, baseline.lastAppliedHeightDp,
                    matches(current, baseline.lastAppliedDensity,
                            baseline.lastAppliedWidthDp, baseline.lastAppliedHeightDp));
        } catch (Throwable t) {
            throw new IllegalStateException("TASK_DENSITY_STATUS_FAILED: " + message(t), t);
        }
    }

    static synchronized String reset(String packageName) {
        try {
            Object task = findTask(packageName);
            if (task == null) return "TASK_NOT_RUNNING " + packageName;
            int taskId = intField(task, "taskId", -1);
            Baseline baseline = BASELINES.remove(taskId);
            if (baseline == null) return "TASK_DENSITY_NO_BASELINE " + packageName;

            applyDensityAndDpSize(task, baseline.density,
                    baseline.screenWidthDp, baseline.screenHeightDp);
            SystemClock.sleep(120L);
            Configuration verified = readCurrentConfiguration(packageName);
            boolean ok = matches(verified, baseline.density,
                    baseline.screenWidthDp, baseline.screenHeightDp);
            return "TASK_DENSITY_RESET " + packageName
                    + " density=" + baseline.density
                    + " dp=" + baseline.screenWidthDp + "x" + baseline.screenHeightDp
                    + " pixels=" + baseline.canvasWidthPx + "x" + baseline.canvasHeightPx
                    + " verified=" + ok;
        } catch (Throwable t) {
            throw new IllegalStateException("TASK_DENSITY_RESET_FAILED: " + message(t), t);
        }
    }

    private static String statusLine(String prefix, String packageName, int taskId,
                                     Baseline baseline, Configuration actual,
                                     int requestedDensity, int requestedWdp,
                                     int requestedHdp, boolean verified) {
        int actualDensity = actual == null ? -1 : actual.densityDpi;
        int actualWdp = actual == null ? -1 : actual.screenWidthDp;
        int actualHdp = actual == null ? -1 : actual.screenHeightDp;
        return prefix + " " + packageName
                + " requested=" + requestedDensity
                + " actual=" + actualDensity
                + " requestedDp=" + requestedWdp + "x" + requestedHdp
                + " actualDp=" + actualWdp + "x" + actualHdp
                + " pixels=" + baseline.canvasWidthPx + "x" + baseline.canvasHeightPx
                + " verified=" + verified
                + " task=" + taskId;
    }

    private static boolean matches(Configuration config, int density, int wDp, int hDp) {
        if (config == null) return false;
        return config.densityDpi == density
                && Math.abs(config.screenWidthDp - wDp) <= 2
                && Math.abs(config.screenHeightDp - hDp) <= 2;
    }

    private static Configuration readCurrentConfiguration(String packageName) throws Exception {
        Object currentTask = findTask(packageName);
        return currentTask == null ? null : taskConfiguration(currentTask);
    }

    private static Object findTask(String packageName) throws Exception {
        Object best = null;
        for (Object task : getTasks(64)) {
            ComponentName top = topActivity(task);
            if (top == null || !packageName.equals(top.getPackageName())) continue;
            if (booleanField(task, "isFocused", false)) return task;
            if (booleanField(task, "isVisible", false)) best = task;
            else if (best == null) best = task;
        }
        return best;
    }

    @SuppressWarnings("unchecked")
    private static List<?> getTasks(int max) throws Exception {
        Class<?> clazz = Class.forName("android.app.ActivityTaskManager");
        Method getInstance = clazz.getDeclaredMethod("getInstance");
        getInstance.setAccessible(true);
        Object manager = getInstance.invoke(null);

        try {
            Method m = clazz.getDeclaredMethod("getTasks", int.class);
            m.setAccessible(true);
            return (List<?>) m.invoke(manager, max);
        } catch (NoSuchMethodException ignored) {}
        try {
            Method m = clazz.getDeclaredMethod("getTasks", int.class, boolean.class);
            m.setAccessible(true);
            return (List<?>) m.invoke(manager, max, false);
        } catch (NoSuchMethodException ignored) {}
        try {
            Method m = clazz.getDeclaredMethod("getTasks", int.class, boolean.class, boolean.class);
            m.setAccessible(true);
            return (List<?>) m.invoke(manager, max, false, false);
        } catch (NoSuchMethodException ignored) {}

        Method m = clazz.getDeclaredMethod("getTasks", int.class, boolean.class, boolean.class, int.class);
        m.setAccessible(true);
        return (List<?>) m.invoke(manager, max, false, false, -1);
    }

    private static ComponentName topActivity(Object task) {
        try {
            Field f = findField(task.getClass(), "topActivity");
            f.setAccessible(true);
            return (ComponentName) f.get(task);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Configuration taskConfiguration(Object task) throws Exception {
        try {
            Method getConfiguration = task.getClass().getMethod("getConfiguration");
            getConfiguration.setAccessible(true);
            Configuration c = (Configuration) getConfiguration.invoke(task);
            return new Configuration(c);
        } catch (Throwable ignored) {
            Field f = findField(task.getClass(), "configuration");
            f.setAccessible(true);
            Configuration c = (Configuration) f.get(task);
            return new Configuration(c);
        }
    }

    private static void applyDensityAndDpSize(Object task, int density,
                                              int screenWidthDp, int screenHeightDp) throws Exception {
        Object token;
        try {
            Method getToken = task.getClass().getMethod("getToken");
            getToken.setAccessible(true);
            token = getToken.invoke(task);
        } catch (Throwable ignored) {
            Field tokenField = findField(task.getClass(), "token");
            tokenField.setAccessible(true);
            token = tokenField.get(task);
        }
        if (token == null) throw new IllegalStateException("Task sem WindowContainerToken");

        Class<?> tokenClass = Class.forName("android.window.WindowContainerToken");
        Class<?> wctClass = Class.forName("android.window.WindowContainerTransaction");
        Constructor<?> wctCtor = wctClass.getDeclaredConstructor();
        wctCtor.setAccessible(true);
        Object wct = wctCtor.newInstance();

        Method setDensity = wctClass.getDeclaredMethod("setDensityDpi", tokenClass, int.class);
        setDensity.setAccessible(true);
        setDensity.invoke(wct, token, density);

        // screenWidthDp/screenHeightDp NÃO são resolução em pixels. Ajustá-los junto
        // da densidade mantém o mesmo canvas em pixels e torna a configuração coerente.
        Method setScreenSizeDp = wctClass.getDeclaredMethod(
                "setScreenSizeDp", tokenClass, int.class, int.class);
        setScreenSizeDp.setAccessible(true);
        setScreenSizeDp.invoke(wct, token, screenWidthDp, screenHeightDp);

        Class<?> organizerClass = Class.forName("android.window.WindowOrganizer");
        Constructor<?> organizerCtor = organizerClass.getDeclaredConstructor();
        organizerCtor.setAccessible(true);
        Object organizer = organizerCtor.newInstance();
        Method apply = organizerClass.getDeclaredMethod("applyTransaction", wctClass);
        apply.setAccessible(true);
        apply.invoke(organizer, wct);
    }

    private static int safeDensity(int density) {
        return Math.max(MIN_DENSITY, Math.min(MAX_DENSITY, density));
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> c = clazz;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static boolean booleanField(Object object, String name, boolean fallback) {
        try {
            Field f = findField(object.getClass(), name);
            f.setAccessible(true);
            return f.getBoolean(object);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int intField(Object object, String name, int fallback) {
        try {
            Field f = findField(object.getClass(), name);
            f.setAccessible(true);
            return f.getInt(object);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String message(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String m = cause.getMessage();
        return m == null || m.trim().isEmpty() ? cause.getClass().getSimpleName() : m;
    }
}
