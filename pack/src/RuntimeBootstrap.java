package dev.yume.loader;

import android.content.pm.ApplicationInfo;

final class RuntimeBootstrap {
    private RuntimeBootstrap() {}

    static ApplicationInfo currentApplicationInfo() {
        return (ApplicationInfo) boundField("appInfo");
    }

    static Object currentLoadedApk() {
        return boundField("info");
    }

    static void installClassLoader(Object loadedApk, ClassLoader classLoader)
            throws ReflectiveOperationException {
        ReflectionAccess.set(loadedApk, "mClassLoader", classLoader);
        ReflectionAccess.setIfPresent(loadedApk, "mDefaultClassLoader", classLoader);
    }

    private static Object boundField(String fieldName) {
        ReflectionAccess.exemptHiddenApis();
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = ReflectionAccess.invokeStatic(
                    activityThread,
                    "currentActivityThread",
                    new Class<?>[0]
            );
            Object boundApplication = ReflectionAccess.get(thread, "mBoundApplication");
            return ReflectionAccess.get(boundApplication, fieldName);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unable to read ActivityThread.AppBindData." + fieldName, error);
        }
    }
}
