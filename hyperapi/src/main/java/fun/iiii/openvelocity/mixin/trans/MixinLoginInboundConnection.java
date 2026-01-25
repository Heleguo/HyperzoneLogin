package fun.iiii.openvelocity.mixin.trans;

import com.velocitypowered.proxy.connection.client.LoginInboundConnection;
import fun.iiii.openvelocity.mixin.IMixinLoginInboundConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = LoginInboundConnection.class)
public class MixinLoginInboundConnection implements IMixinLoginInboundConnection {

    @Shadow
    void loginEventFired(Runnable onAllMessagesHandled){}


    public void fireLogin(Runnable onAllMessagesHandled){
        loginEventFired(onAllMessagesHandled);
    }
}
