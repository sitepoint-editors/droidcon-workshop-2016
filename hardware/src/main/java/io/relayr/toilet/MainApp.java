package io.relayr.toilet;

import android.app.Application;

import io.relayr.android.RelayrSdk;

public class MainApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        new RelayrSdk.Builder(this).cacheModels(false).build();
    }
}