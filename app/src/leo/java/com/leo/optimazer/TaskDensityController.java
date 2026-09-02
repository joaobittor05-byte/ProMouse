package com.leo.optimazer;

import android.content.ComponentName;
import android.content.res.Configuration;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aplica densityDpi somente ao WindowContainer/Task de um aplicativo.
 * Não altera bounds, screen size, wm size ou wm density do dispositivo.
 *
 * Esta API é @hide no Android; o Leo a executa dentro do UserService do Shizuku
 * com UID shell/root e faz feature detection em runtime para evitar assumir que
 * todos os OEMs mantêm exatamente a mesma implementação.
 */
final class TaskDensityController {
    private static final int MIN_DENSITY = 72;
    private static final int MAX_DENSITY = 1000;

    private static final class Baseline {
        final String packageName;
        final int density;
        int lastApplied;

        Baseline(String packageName, int density) {
            this.packageName = packageName;
            this.density = density;
            this.lastApplied = density;
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
                if (booleanField(task, "isFocused", false)) {
                    return top.getPackageName();
                }
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
        int density = Math.max(MIN_DENSITY, Math.min(MAX_DENSITY, requestedDensity));
        try {
            Object task = findTask(packageName);
            if (task == null) return "TASK_NOT_RUNNING " + packageName;

            int taskId = intField(task, "taskId", -1);
            int currentDensity = taskDensity(task);
            Baseline baseline = BASELINES.get(taskId);
            if (baseline == null || !baseline.packageName.equals(packageName)) {
                baseline = new Baseline(packageName, currentDensity);
                BASELINES.put(taskId, baseline);
            }

            if (baseline.lastApplied == density && currentDensity == density) {
                return "TASK_DENSITY_ALREADY " + packageName + " " + density;
            }

            applyDensity(task, density);
            baseline.lastApplied = density;
            return "TASK_DENSITY_OK " + packageName
                    + " base=" + baseline.density
                    + " applied=" + density
                    + " task=" + taskId;
        } catch (Throwable t) {
            throw new IllegalStateException("TASK_DENSITY_UNSUPPORTED: " + message(t), t);
        }
    }

    static synchronized String reset(String packageName) {
        try {
            Object task = findTask(packageName);
            if (task == null) return "TASK_NOT_RUNNING " + packageName;
            int taskId = intField(task, "taskId", -1);
            Baseline baseline = BASELINES.remove(taskId);
            if (baseline == null) return "TASK_DENSITY_NO_BASELINE " + packageName;
            applyDensity(task, baseline.density);
            return "TASK_DENSITY_RESET " + packageName + " " + baseline.density;
        } catch (Throwable t) {
            throw new IllegalStateException("TASK_DENSITY_RESET_FAILED: " + message(t), t);
        }
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

    private static ComponentName topActivity(Object task) throws Exception {
        try {
            Field f = findField(task.getClass(), "topActivity");
            f.setAccessible(true);
            return (ComponentName) f.get(task);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int taskDensity(Object task) throws Exception {
        Configuration configuration;
        try {
            Method getConfiguration = task.getClass().getMethod("getConfiguration");
            getConfiguration.setAccessible(true);
            configuration = (Configuration) getConfiguration.invoke(task);
        } catch (Throwable ignored) {
            Field f = findField(task.getClass(), "configuration");
            f.setAccessible(true);
            configuration = (Configuration) f.get(task);
        }
        return Math.max(MIN_DENSITY, configuration.densityDpi);
    }

    private static void applyDensity(Object task, int density) throws Exception {
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

        Class<?> organizerClass = Class.forName("android.window.WindowOrganizer");
        Constructor<?> organizerCtor = organizerClass.getDeclaredConstructor();
        organizerCtor.setAccessible(true);
        Object organizer = organizerCtor.newInstance();
        Method apply = organizerClass.getDeclaredMethod("applyTransaction", wctClass);
        apply.setAccessible(true);
        apply.invoke(organizer, wct);
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
