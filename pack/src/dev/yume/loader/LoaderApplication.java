package dev.yume.loader;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.NonNull;

public final class LoaderApplication extends Application {
    private Application original;

    @Override
    protected void attachBaseContext(@NonNull Context base) {
        super.attachBaseContext(base);
        PayloadInstaller.Installation installation = PayloadInstaller.install(
                base.getApplicationInfo(),
                base.getClassLoader(),
                RuntimeBootstrap.currentLoadedApk()
        );
        original = ApplicationBridge.create(
                base,
                installation.classLoader(),
                installation.metadata().originalApplication
        );
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ApplicationBridge.replace(this, original);
        original.onCreate();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        original.onConfigurationChanged(newConfig);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        original.onLowMemory();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        original.onTrimMemory(level);
    }

    @Override
    public void onTerminate() {
        original.onTerminate();
        super.onTerminate();
    }
}
