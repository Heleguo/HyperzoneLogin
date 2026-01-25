package fun.iiii.openvelocity.mixin.trans;

import com.velocitypowered.proxy.VelocityServer;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VelocityServer.class)
public class MixinVelocity {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstruction(CallbackInfo ci) {
        Logger testLogger=LogManager.getLogger(VelocityServer.class);
        testLogger.info("Hello World!");
        System.out.println("HTT");
    }
}
