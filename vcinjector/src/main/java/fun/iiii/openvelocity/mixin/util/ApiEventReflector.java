package fun.iiii.openvelocity.mixin.util;

import com.velocitypowered.api.util.GameProfile;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

public final class ApiEventReflector {

    private static volatile boolean initialized = false;

    private static Constructor<?> openPreLoginEventCtor;
    private static Method openPreLoginEventIsOnline;

    private static Constructor<?> onlineAuthEventCtor;
    private static Method onlineAuthEventGetGameProfile;

    private ApiEventReflector() {
    }

    public static boolean tryInit() {
        if (initialized) {
            return true;
        }

        synchronized (ApiEventReflector.class) {
            if (initialized) {
                return true;
            }

            try {
                Class<?> openPreLoginEventClass = PluginClassLoaderReflector.loadClassFromLoaders(
                        "fun.iiii.h2l.api.event.connection.OpenPreLoginEvent"
                );
                openPreLoginEventCtor = openPreLoginEventClass.getConstructor(UUID.class, String.class, String.class);
                openPreLoginEventIsOnline = openPreLoginEventClass.getMethod("isOnline");

                Class<?> onlineAuthEventClass = PluginClassLoaderReflector.loadClassFromLoaders(
                        "fun.iiii.h2l.api.event.connection.OnlineAuthEvent"
                );
                onlineAuthEventCtor = onlineAuthEventClass.getConstructor(
                        String.class,
                        UUID.class,
                        String.class,
                        String.class,
                        boolean.class
                );
                onlineAuthEventGetGameProfile = onlineAuthEventClass.getMethod("getGameProfile");
                initialized = true;
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        if (!tryInit()) {
            throw new IllegalStateException("ApiEventReflector not initialized. Call tryInit() when plugin classloaders are available.");
        }
    }

    public static Object createOpenPreLoginEvent(UUID uuid, String userName, String host) {
        try {
            ensureInitialized();
            return openPreLoginEventCtor.newInstance(uuid, userName, host);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create OpenPreLoginEvent", e);
        }
    }

    public static boolean isOpenPreLoginOnline(Object event) {
        try {
            ensureInitialized();
            return (boolean) openPreLoginEventIsOnline.invoke(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to call OpenPreLoginEvent#isOnline", e);
        }
    }

    public static Object createOnlineAuthEvent(String userName, UUID userUUID, String serverId, String playerIp, boolean isOnline) {
        try {
            ensureInitialized();
            return onlineAuthEventCtor.newInstance(userName, userUUID, serverId, playerIp, isOnline);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create OnlineAuthEvent", e);
        }
    }

    public static GameProfile getOnlineAuthGameProfile(Object event) {
        try {
            ensureInitialized();
            return (GameProfile) onlineAuthEventGetGameProfile.invoke(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to call OnlineAuthEvent#getGameProfile", e);
        }
    }
}