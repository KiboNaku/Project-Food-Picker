package me.nakukibo.projectfoodpicker;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public class FoodPicker extends Application {
    private static Context app;
    private static SharedPreferences sharedPreferences;
    private static SharedPreferences.Editor editor;

    @Override
    public void onCreate() {
        super.onCreate();
        app = this;
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(app);
        editor = sharedPreferences.edit();
    }

    public static Context getApp(){
        return app;
    }

    public static SharedPreferences getSharedPreferences(){
        return sharedPreferences;
    }

    public static SharedPreferences.Editor getEditor(){
        return editor;
    }
}