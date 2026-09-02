package com.leo.optimazer;

import android.app.Application;

public class LeoApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        BridgeClient.initialize(this);
    }
}
