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
    private static final Object LOCK = new Object();

    private static volatile Context appContext;
    private static volatile ILeoShell service;
    private static volatile boolean binding;
    private static volatile Shizuku.UserServiceArgs serviceArgs;

    private ShizukuCore() {}

    private static final ServiceConnection CONNECTION = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ILeoShell.Stub.asInterface(binder);
            binding = false;
            synchronized (LOCK) { LOCK.notifyAll(); }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            binding = false;
            synchronized (LOCK) { LOCK.notifyAll(); }
        }
    };

    private static final Shizuku.OnBinderReceivedListener BINDER_RECEIVED = () -> {
        if (hasPermission()) bindUserService();
    };

    private static final Shizuku.OnBinderDeadListener BINDER_DEAD = () -> {
        service = null;
        binding = false;
        synchronized (LOCK) { LOCK.notifyAll(); }
    };

    private static final Shizuku.OnRequestPermissionResultListener PERMISSION_RESULT = (requestCode, grantResult) -> {
        if (requestCode == REQUEST_CODE && grantResult == PackageManager.PERMISSION_GRANTED) {
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
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean hasPermission() {
        if (!isBinderAlive()) return false;
        try {
            if (Shizuku.isPreV11()) return false;
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isReady() {
        ILeoShell local = service;
        if (local == null) return false;
        try {
            return local.asBinder().isBinderAlive();
        } catch (Throwable ignored) {
            service = null;
            return false;
        }
    }

    public static int getBackendUid() {
        if (!isBinderAlive()) return -1;
        try {
            return Shizuku.getUid();
        } catch (Throwable ignored) {
            return -1;
        }
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
        if (context == null || !hasPermission() || isReady() || binding) return;
        binding = true;
        try {
            if (serviceArgs == null) {
                serviceArgs = new Shizuku.UserServiceArgs(new ComponentName(context, LeoShizukuService.class))
                        .processNameSuffix("leo_shell")
                        .daemon(true)
                        .tag("leo_optimazer_shell")
                        .debuggable(false)
                        .version(5);
            }
            Shizuku.bindUserService(serviceArgs, CONNECTION);
        } catch (Throwable t) {
            binding = false;
            synchronized (LOCK) { LOCK.notifyAll(); }
        }
    }

    public static String execute(String command) throws Exception {
        if (!hasPermission()) throw new IllegalStateException("Permissão do Shizuku não concedida");
        bindUserService();

        long deadline = SystemClock.elapsedRealtime() + 4000L;
        while (!isReady() && SystemClock.elapsedRealtime() < deadline) {
            synchronized (LOCK) {
                try { LOCK.wait(120L); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Conexão Shizuku interrompida");
                }
            }
        }

        ILeoShell local = service;
        if (local == null) throw new IllegalStateException("UserService do Shizuku não conectou");
        return local.execute(command);
    }

    public static int getServiceUid() {
        ILeoShell local = service;
        if (local == null) return -1;
        try {
            return local.getServiceUid();
        } catch (Exception ignored) {
            return -1;
        }
    }

    public static String statusLabel() {
        if (!isManagerInstalled()) return "SHIZUKU NÃO INSTALADO";
        if (!isBinderAlive()) return "SHIZUKU PARADO";
        if (!hasPermission()) return "PERMISSÃO DO SHIZUKU PENDENTE";
        if (!isReady()) return "CONECTANDO AO NÚCLEO SHIZUKU";
        int uid = getServiceUid();
        return uid == 0 ? "SHIZUKU ROOT ATIVO" : "SHIZUKU SHELL ATIVO";
    }
}
