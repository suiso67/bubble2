package com.nkanaev.comics;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDexApplication;


public class MainApplication extends MultiDexApplication {
    private static Application instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // apply them from settings
        int default_mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        int mode = getPreferences().getInt(Constants.SETTINGS_THEME, default_mode);
        // make extra sure we got a legal mode value
        switch (mode) {
            case AppCompatDelegate.MODE_NIGHT_NO:
            case AppCompatDelegate.MODE_NIGHT_YES:
            case AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM:
                break;
            default:
                mode = default_mode;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    public static Context getAppContext() {
        return instance.getApplicationContext();
    }

    public static SharedPreferences getPreferences() {
        return instance.getSharedPreferences(Constants.SETTINGS_NAME, 0);
    }
}
