package dev.yume.loader;

import android.app.Activity;
import android.app.AppComponentFactory;
import android.app.Application;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.Intent;
import android.content.pm.ApplicationInfo;

import java.lang.reflect.InvocationTargetException;

public final class LoaderComponentFactory extends AppComponentFactory {
    private volatile AppComponentFactory delegate;
    private volatile ClassLoader payloadLoader;

    @Override
    public Application instantiateApplication(ClassLoader classLoader, String className)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        PayloadInstaller.Installation installation = prepare(classLoader);
        return delegate(installation.metadata).instantiateApplication(
                installation.classLoader,
                installation.metadata.originalApplication
        );
    }

    @Override
    public Activity instantiateActivity(ClassLoader classLoader, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        PayloadInstaller.Installation installation = prepare(classLoader);
        return delegate(installation.metadata).instantiateActivity(installation.classLoader, className, intent);
    }

    @Override
    public Service instantiateService(ClassLoader classLoader, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        PayloadInstaller.Installation installation = prepare(classLoader);
        return delegate(installation.metadata).instantiateService(installation.classLoader, className, intent);
    }

    @Override
    public BroadcastReceiver instantiateReceiver(ClassLoader classLoader, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        PayloadInstaller.Installation installation = prepare(classLoader);
        return delegate(installation.metadata).instantiateReceiver(installation.classLoader, className, intent);
    }

    @Override
    public ContentProvider instantiateProvider(ClassLoader classLoader, String className)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        PayloadInstaller.Installation installation = prepare(classLoader);
        return delegate(installation.metadata).instantiateProvider(installation.classLoader, className);
    }

    private PayloadInstaller.Installation prepare(ClassLoader classLoader) {
        ApplicationInfo appInfo = RuntimeBootstrap.currentApplicationInfo();
        PayloadInstaller.Installation installation = PayloadInstaller.install(
                appInfo,
                classLoader,
                RuntimeBootstrap.currentLoadedApk()
        );
        payloadLoader = installation.classLoader;
        return installation;
    }

    private AppComponentFactory delegate(PayloadMetadata metadata) throws ClassNotFoundException,
            IllegalAccessException, InstantiationException {
        AppComponentFactory current = delegate;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (delegate == null) {
                if (metadata.originalComponentFactory.isEmpty()) {
                    delegate = new AppComponentFactory();
                } else {
                    Class<?> type = Class.forName(metadata.originalComponentFactory, true, payloadLoader);
                    delegate = instantiateComponentFactory(type);
                }
            }
            return delegate;
        }
    }

    private static AppComponentFactory instantiateComponentFactory(Class<?> type)
            throws IllegalAccessException, InstantiationException {
        try {
            return type.asSubclass(AppComponentFactory.class).getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException | InvocationTargetException e) {
            InstantiationException failure = new InstantiationException(type.getName());
            failure.initCause(e);
            throw failure;
        }
    }
}
