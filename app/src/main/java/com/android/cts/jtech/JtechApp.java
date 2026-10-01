package com.android.cts.jtech;

import android.app.Application;

public class JtechApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Hardware soft keys (vendored Yapchik engine); a no-op below API 26.
        JtechSoftkeys.init(this);
    }
}
