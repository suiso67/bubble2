package com.nkanaev.comics;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.multidex.MultiDexApplication;


public class MainApplication extends MultiDexApplication {
    private static Application instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static Context getAppContext() {
        return instance.getApplicationContext();
    }

    public static SharedPreferences getPreferences() {
        return instance.getSharedPreferences(Constants.SETTINGS_NAME, 0);
    }
}
