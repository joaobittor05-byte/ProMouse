package com.leo.optimazer;

import android.app.Application;
import android.content.Intent;
import android.os.Build;

public class LeoApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ShizukuCore.initialize(this);
        ShizukuCore.bindUserService();

        // Perfis de DPI independem do temporizador de RAM. Se já existe algum perfil,
        // mantenha o monitor de foreground disponível quando o Leo for aberto.
        if (!ProfileStore.all(this).isEmpty()) {
            try {
                Intent intent = new Intent(this, MonitorService.class);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
                else startService(intent);
            } catch (Throwable ignored) {
                // O usuário pode abrir a tela principal e o serviço será iniciado novamente.
            }
        }
    }
}
