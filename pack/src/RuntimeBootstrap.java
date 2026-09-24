package dev.flycat.loader;

import android.content.pm.ApplicationInfo;

final class RuntimeBootstrap {
    private static final int BOUND_APPLICATION_PRESENT = 1;
    private static final int BOUND_APPLICATION_ABSENT = 2;
    private static final int BOUND_APPLICATION_ASSUMED = 3;
    /** "0 表示尚未解决；否则为 {@code BOUND_APPLICATION_*} 状态之一。" */
    private static volatile int boundApplicationState;

    private RuntimeBootstrap() {}

    /**
     ```
     * 此进程是否为应用进程，即{@code ActivityThread} {@code mBoundApplication}是否已设置。
     * 仅为其他应用托管类的进程（Shizuku 使用{@code ActivityThread.systemMain()}启动其UserService）没有该设置，且不能在此处安装载荷。
     *
     * <p>每个进程解析一次。无法读取的状态将保持应用假设，因此反射失败永远不会禁用载荷安装。
     ```
     */
    static boolean hasBoundApplication() {
        int resolved = boundApplicationState;
        if (resolved == 0) {
            resolved = classifyBoundApplication();
            if (resolved == 0) {
                // 反射报告为"未知"；回退到应用假定。
                resolved = BOUND_APPLICATION_ASSUMED;
            }
            boundApplicationState = resolved;
        }
        // 只有显式的"absent"才会禁用负载安装。
        return resolved != BOUND_APPLICATION_ABSENT;
    }

    /**
     * @return {@link #BOUND_APPLICATION_PRESENT} 或 {@link #BOUND_APPLICATION_ABSENT}，或者 {@code 0} 当状态无法被读取时。
     */
    private static int classifyBoundApplication() {
        try {
            ReflectionAccess.exemptHiddenApis();
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = ReflectionAccess.invokeStatic(activityThread, "currentActivityThread", new Class<?>[0]);
            if (thread == null) {
                return 0;
            }
            return ReflectionAccess.get(thread, "mBoundApplication") != null
                    ? BOUND_APPLICATION_PRESENT
                    : BOUND_APPLICATION_ABSENT;
        } catch (Throwable error) {
            return 0;
        }
    }

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
