package co.ke.bremac.posapp;

import android.app.Application;

public class PosApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ThemeMode.applySaved(this);
    }
}
