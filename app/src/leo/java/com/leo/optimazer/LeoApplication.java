package com.leo.optimazer;

import android.app.Application;
import android.content.Intent;
import android.os.Build;

public class LeoApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ShizukuCore.initialize(this);

        // Não faça bind do UserService dentro de Application.onCreate.
        // Em algumas ROMs Xiaomi/MediaTek isso pode acontecer cedo demais e deixar
        // a conexão presa. A tela de ativação inicia o bind após a UI estar pronta;
        // se a ROM ainda bloquear UserService, o Shell Compat assume automaticamente.

        if (!ProfileStore.all(this).isEmpty()) {
            try {
                Intent intent = new Intent(this, MonitorService.class);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
                else startService(intent);
            } catch (Throwable ignored) {
            }
        }
    }
}
