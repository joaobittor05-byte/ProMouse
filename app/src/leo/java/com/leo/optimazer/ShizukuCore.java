package com.leo.optimazer;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.SystemClock;

import rikka.shizuku.Shizuku;

public final class ShizukuCore {
    public static final int REQUEST_CODE = 4105;
    private static final int USER_SERVICE_VERSION = 13;
    private static final long BIND_TIMEOUT_MS = 5000L;
    private static final Object LOCK = new Object();

    private static volatile Context appContext;
    private static volatile ILeoShell service;
    private static volatile boolean binding;
    private static volatile long bindStartedAt;
    private static volatile String lastBindError = "";
    private static volatile Shizuku.UserServiceArgs serviceArgs;

    private ShizukuCore() {}

    private static final ServiceConnection CONNECTION = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ILeoShell.Stub.asInterface(binder);
            binding = false;
            bindStartedAt = 0L;
            lastBindError = "";
            synchronized (LOCK) { LOCK.notifyAll(); }
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            binding = false;
            bindStartedAt = 0L;
            lastBindError = "UserService desconectado";
            synchronized (LOCK) { LOCK.notifyAll(); }
        }
    };

    private static final Shizuku.OnBinderReceivedListener BINDER_RECEIVED = () -> {
        lastBindError = "";
        if (hasPermission()) bindUserService();
    };

    private static final Shizuku.OnBinderDeadListener BINDER_DEAD = () -> {
        service = null;
        binding = false;
        bindStartedAt = 0L;
        lastBindError = "Binder do Shizuku morreu";
        synchronized (LOCK) { LOCK.notifyAll(); }
    };

    private static final Shizuku.OnRequestPermissionResultListener PERMISSION_RESULT = (requestCode, grantResult) -> {
        if (requestCode == REQUEST_CODE && grantResult == PackageManager.PERMISSION_GRANTED) {
            lastBindError = "";
            bindUserService();
        }
    };

    public static void initialize(Context context) {
        if (appContext != null) return;
        appContext = context.getApplicationContext();
        Shizuku.addBinderReceivedListenerSticky(BINDER_RECEIVED);
        Shizuku.addBinderDeadListener(BINDER_DEAD);
        Shizuku.addRequestPermissionResultListener(PERMISSION_RESULT);
    }

    public static boolean isManagerInstalled() {
        Context context = appContext;
        if (context == null) return false;
        try {
            context.getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isBinderAlive() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            lastBindError = "Binder indisponível: " + safeMessage(t);
            return false;
        }
    }

    public static boolean hasPermission() {
        if (!isBinderAlive()) return false;
        try {
            if (Shizuku.isPreV11()) {
                lastBindError = "Shizuku antigo demais";
                return false;
            }
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            lastBindError = "Falha ao verificar permissão: " + safeMessage(t);
            return false;
        }
    }

    public static boolean isReady() {
        ILeoShell local = service;
        if (local == null) return false;
        try {
            return local.asBinder().isBinderAlive();
        } catch (Throwable t) {
            service = null;
            lastBindError = "UserService inválido: " + safeMessage(t);
            return false;
        }
    }

    public static boolean isBinding() {
        if (binding && bindStartedAt > 0L
                && SystemClock.elapsedRealtime() - bindStartedAt > BIND_TIMEOUT_MS) {
            binding = false;
            bindStartedAt = 0L;
            lastBindError = "Tempo limite ao conectar UserService";
        }
        return binding;
    }

    public static String getLastBindError() {
        return lastBindError == null ? "" : lastBindError;
    }

    public static int getBackendUid() {
        if (!isBinderAlive()) return -1;
        try { return Shizuku.getUid(); } catch (Throwable ignored) { return -1; }
    }

    public static void requestPermission() {
        if (!isBinderAlive()) throw new IllegalStateException("Inicie o Shizuku primeiro");
        if (hasPermission()) {
            bindUserService();
            return;
        }
        Shizuku.requestPermission(REQUEST_CODE);
    }

    public static void bindUserService() {
        Context context = appContext;
        if (context == null) {
            lastBindError = "LeoApplication ainda não inicializado";
            return;
        }
        if (!hasPermission() || isReady()) return;
        if (isBinding()) return;

        binding = true;
        bindStartedAt = SystemClock.elapsedRealtime();
        lastBindError = "";
        try {
            if (serviceArgs == null) {
                serviceArgs = new Shizuku.UserServiceArgs(
                        new ComponentName(context.getPackageName(), LeoShizukuService.class.getName()))
                        .processNameSuffix("leo_shell")
                        .daemon(true)
                        .tag("leo_optimazer_shell")
                        .debuggable(false)
                        .version(USER_SERVICE_VERSION);
            }
            Shizuku.bindUserService(serviceArgs, CONNECTION);
        } catch (Throwable t) {
            binding = false;
            bindStartedAt = 0L;
            lastBindError = "Falha no bind: " + safeMessage(t);
            synchronized (LOCK) { LOCK.notifyAll(); }
        }
    }

    public static void retryBind() {
        if (isReady()) return;
        binding = false;
        bindStartedAt = 0L;
        lastBindError = "";
        bindUserService();
    }

    public static String execute(String command) throws Exception {
        if (!hasPermission()) throw new IllegalStateException("Permissão do Shizuku não concedida");
        bindUserService();

        long deadline = SystemClock.elapsedRealtime() + BIND_TIMEOUT_MS;
        while (!isReady() && SystemClock.elapsedRealtime() < deadline) {
            synchronized (LOCK) {
                try {
                    LOCK.wait(120L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Conexão Shizuku interrompida");
                }
            }
            if (!isReady() && !isBinding()) bindUserService();
        }

        ILeoShell local = service;
        if (local == null) {
            String detail = getLastBindError();
            throw new IllegalStateException(detail.isEmpty()
                    ? "UserService do Shizuku não conectou" : detail);
        }
        return local.execute(command);
    }

    public static int getServiceUid() {
        ILeoShell local = service;
        if (local == null) return -1;
        try { return local.getServiceUid(); } catch (Exception ignored) { return -1; }
    }

    public static String statusLabel() {
        if (!isManagerInstalled()) return "SHIZUKU NÃO INSTALADO";
        if (!isBinderAlive()) return "SHIZUKU PARADO";
        if (!hasPermission()) return "PERMISSÃO DO SHIZUKU PENDENTE";
        if (!isReady()) {
            String error = getLastBindError();
            if (!error.isEmpty()) return "NÚCLEO NÃO CONECTOU • " + error;
            return isBinding() ? "CONECTANDO AO NÚCLEO SHIZUKU" : "NÚCLEO SHIZUKU DESCONECTADO";
        }
        int uid = getServiceUid();
        return uid == 0 ? "SHIZUKU ROOT ATIVO" : "SHIZUKU SHELL ATIVO";
    }

    private static String safeMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.trim().isEmpty()
                ? cause.getClass().getSimpleName() : message;
    }
}
