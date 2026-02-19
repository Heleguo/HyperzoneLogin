package fun.iiii.openvelocity.mixin.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class PluginClassLoaderReflector {

    private static final Set<?> LOADER_SET_REF;
    private static final Method LOAD_CLASS_METHOD;

    static {
        try {
            Class<?> pluginClassLoaderClass = Class.forName("com.velocitypowered.proxy.plugin.PluginClassLoader");

            Field loadersField = pluginClassLoaderClass.getDeclaredField("loaders");
            loadersField.setAccessible(true);
            Object loaders = loadersField.get(null);
            if (!(loaders instanceof Set<?>)) {
                throw new IllegalStateException("PluginClassLoader.loaders is not a Set");
            }
            LOADER_SET_REF = (Set<?>) loaders;

            LOAD_CLASS_METHOD = pluginClassLoaderClass.getMethod("loadClass", String.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize PluginClassLoader reflection access", e);
        }
    }

    private PluginClassLoaderReflector() {
    }

    public static Set<ClassLoader> getLoadersSnapshot() {
        Set<ClassLoader> snapshot = new LinkedHashSet<>();
        for (Object loader : LOADER_SET_REF) {
            if (loader instanceof ClassLoader) {
                snapshot.add((ClassLoader) loader);
            }
        }
        return Collections.unmodifiableSet(snapshot);
    }

    public static Class<?> loadClassFromLoaders(String className) throws ClassNotFoundException {
        ClassNotFoundException notFound = new ClassNotFoundException("Class not found in any PluginClassLoader: " + className);

        for (Object loader : LOADER_SET_REF) {
            try {
                return (Class<?>) LOAD_CLASS_METHOD.invoke(loader, className);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ClassNotFoundException) {
                    notFound.addSuppressed(cause);
                    continue;
                }
                throw new RuntimeException("Failed to invoke loadClass on PluginClassLoader", cause);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Cannot access loadClass on PluginClassLoader", e);
            }
        }

        throw notFound;
    }
}
