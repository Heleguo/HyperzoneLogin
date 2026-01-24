package fun.iiii.openvelocity.mixin;

import com.velocitypowered.proxy.connection.client.LoginInboundConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

public interface IMixinLoginInboundConnection {
    public void fireLogin(Runnable onAllMessagesHandled);
}
