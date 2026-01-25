package fun.iiii.openvelocity.mixin.util;

import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.AuthSessionHandler;
import com.velocitypowered.proxy.connection.client.LoginInboundConnection;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class AuthSessionHandlerFactory {

    private static final MethodHandle CONSTRUCTOR_HANDLE;

    static {
        try {
            // 1. 获取目标类的 Class 对象
            Class<?> authSessionClass = Class.forName("com.velocitypowered.proxy.connection.client.AuthSessionHandler");

            // 2. 获取调用者的 Lookup (即当前工厂类的 Lookup)
            MethodHandles.Lookup callerLookup = MethodHandles.lookup();

            // 3. 关键步骤：使用 privateLookupIn 创建一个“拥有目标类权限”的 Lookup
            // 这相当于获得了一个在该类内部编写代码的 Lookup 权限
            MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(authSessionClass, callerLookup);

            // 4. 定义方法类型
            // 注意：参数类型必须与构造函数完全匹配，包括 boolean.class
            MethodType methodType = MethodType.methodType(void.class,
                    com.velocitypowered.proxy.VelocityServer.class,
                    com.velocitypowered.proxy.connection.client.LoginInboundConnection.class,
                    com.velocitypowered.api.util.GameProfile.class,
                    boolean.class
            );

            // 5. 使用拥有特权的 privateLookup 查找构造函数
            CONSTRUCTOR_HANDLE = privateLookup.findConstructor(authSessionClass, methodType);

        } catch (IllegalAccessException e) {
            // 如果依然抛出异常，说明模块系统限制了该加载器对另一个模块的深度反射
            throw new RuntimeException("Module access denied: Cannot access private constructor of AuthSessionHandler. " +
                    "Ensure your module/export configuration allows deep reflection.", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve AuthSessionHandler constructor", e);
        }
    }

    public static AuthSessionHandler create(VelocityServer server, LoginInboundConnection inbound, GameProfile profile, boolean onlineMode) {
        try {
            // 返回 Object 因为你可能无法直接引用 AuthSessionHandler 类（如果它不是 public）
            return (AuthSessionHandler) CONSTRUCTOR_HANDLE.invokeExact(server, inbound, profile, onlineMode);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to instantiate AuthSessionHandler", e);
        }
    }
}
