package dev.flycat.loader;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

public final class LoaderApplication extends Application {
    private Application original;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (!RuntimeBootstrap.hasBoundApplication()) {
            // 非应用进程（例如 Shizuku UserService）：无需安装有效负载。
            return;
        }
        PayloadInstaller.Installation installation = PayloadInstaller.install(
                base.getApplicationInfo(),
                base.getClassLoader(),
                RuntimeBootstrap.currentLoadedApk()
        );
        original = ApplicationBridge.create(
                base,
                installation.classLoader,
                installation.metadata.originalApplication
        );
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (original == null) {
            return;
        }
        ApplicationBridge.replace(this, original);
        original.onCreate();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (original != null) {
            original.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (original != null) {
            original.onLowMemory();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (original != null) {
            original.onTrimMemory(level);
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (original != null) {
            original.onTerminate();
        }
    }
}
