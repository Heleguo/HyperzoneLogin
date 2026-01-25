package fun.iiii.openvelocity.mixin.util;

import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.AuthSessionHandler;
import com.velocitypowered.proxy.connection.client.LoginInboundConnection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class AuthSessionHandlerFactory {

    // 1. 定义静态句柄，避免每次调用都查找
    private static final MethodHandle CONSTRUCTOR_HANDLE;

    static {
        try {
            // 获取 Lookup 对象
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            // 定义构造函数的方法类型：void 返回值，参数依次为对应类
            // 注意 boolean 使用 boolean.class
            MethodType methodType = MethodType.methodType(void.class,
                    com.velocitypowered.proxy.VelocityServer.class, // 替换为实际的类全名
                    com.velocitypowered.proxy.connection.client.LoginInboundConnection.class,
                    com.velocitypowered.api.util.GameProfile.class,
                    boolean.class
            );

            // 查找构造函数并创建句柄
            CONSTRUCTOR_HANDLE = lookup.findConstructor(AuthSessionHandler.class, methodType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve AuthSessionHandler constructor", e);
        }
    }

    /**
     * 高效创建实例的方法
     */
    public static AuthSessionHandler create(VelocityServer server, LoginInboundConnection inbound, GameProfile profile, boolean onlineMode) {
        try {
            // 2. 使用 invokeExact 调用（性能最高，但参数类型需严格匹配）
            // invokeExact 会进行严格类型匹配，返回值为 Object 需要强转
            return (AuthSessionHandler) CONSTRUCTOR_HANDLE.invokeExact(server, inbound, profile, onlineMode);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to instantiate AuthSessionHandler", e);
        }
    }
}
