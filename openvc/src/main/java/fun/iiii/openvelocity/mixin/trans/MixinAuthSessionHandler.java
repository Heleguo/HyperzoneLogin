package fun.iiii.openvelocity.mixin.trans;

import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.AuthSessionHandler;
import com.velocitypowered.proxy.connection.client.LoginInboundConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = AuthSessionHandler.class)
public class MixinAuthSessionHandler {
    @Invoker("<init>")
    static AuthSessionHandler newHandler(VelocityServer server, LoginInboundConnection inbound, GameProfile profile, boolean onlineMode) {
        throw new AssertionError();
    }
}
