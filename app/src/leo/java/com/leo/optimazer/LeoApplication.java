package com.leo.optimazer;

import android.app.Application;
import android.content.Intent;
import android.os.Build;

public class LeoApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ShizukuCore.initialize(this);

        // O bind do UserService continua atrasado para evitar o travamento observado
        // em algumas ROMs. Os serviços usam UserService quando disponível e Shell Compat
        // como fallback, sem amarrar o app a um fabricante ou processador específico.
        if (!ProfileStore.all(this).isEmpty()) {
            startCompatService(new Intent(this, MonitorService.class));
            startCompatService(new Intent(this, FrameRepeatService.class));
        }
    }

    private void startCompatService(Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
        } catch (Throwable ignored) {}
    }
}
