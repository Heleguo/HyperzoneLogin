package fun.iiii.hyperzone.login.limbo;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import fun.iiii.hyperzone.login.limbo.handler.LimboAuthSessionHandler;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.chunk.Dimension;
import net.elytrium.limboapi.api.chunk.VirtualWorld;
import net.elytrium.limboapi.api.event.LoginLimboRegisterEvent;
import net.elytrium.limboapi.api.player.GameMode;

public class LimboAuth {
    private final LimboFactory factory;
    private Limbo authServer;

    public LimboAuth(ProxyServer server) {
        this.factory = (LimboFactory) server.getPluginManager().getPlugin("limboapi").flatMap(PluginContainer::getInstance).orElseThrow();

    }

    public void load() {
        VirtualWorld authWorld = this.factory.createVirtualWorld(
                Dimension.THE_END,
                0, 0, 0,
                (float) 0, (float) 0
        );

        this.authServer = this.factory
                .createLimbo(authWorld)
                .setName("HyperzoneLogin")
                .setWorldTime(1000L)
                .setGameMode(GameMode.ADVENTURE);

    }

//    依靠GameProfileRequestEvent，到这里我们的验证早就结束了，这里的onlineMode应该是正确的
    @Subscribe
    public void onLoginLimboRegister(LoginLimboRegisterEvent event) {
//        在线不验证
        if (event.getPlayer().isOnlineMode()) {
            return;
        }

//        必须callBack
        event.addOnJoinCallback(() -> this.authPlayer(event.getPlayer()));
    }

    public void authPlayer(Player player) {
//        this.factory.passLoginLimbo(player);
        this.authServer.spawnPlayer(player, new LimboAuthSessionHandler(player));
    }
}
