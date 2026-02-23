package icu.h2l.login.limbo

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import icu.h2l.api.event.limbo.LimboAuthStartEvent
import icu.h2l.api.limbo.HyperZoneLimbo
import icu.h2l.login.limbo.handler.LimboAuthSessionHandler
import icu.h2l.login.manager.HyperZonePlayerManager
import icu.h2l.login.HyperZoneLoginMain
import net.elytrium.limboapi.api.Limbo
import net.elytrium.limboapi.api.LimboFactory
import net.elytrium.limboapi.api.chunk.Dimension
import net.elytrium.limboapi.api.chunk.VirtualWorld
import net.elytrium.limboapi.api.event.LoginLimboRegisterEvent
import net.elytrium.limboapi.api.player.GameMode

//用于注册到limbo服务器，不需要融合于任何模块目前
class LimboAuth(server: ProxyServer) : HyperZoneLimbo {
    private val factory: LimboFactory
    private lateinit var limboAuthServer: Limbo
    override val authServer: Limbo
        get() = limboAuthServer

    init {
        factory = server.pluginManager.getPlugin("limboapi")
            .flatMap { obj: PluginContainer -> obj.instance }
            .orElseThrow() as LimboFactory
    }

    fun load() {
        val authWorld: VirtualWorld = factory.createVirtualWorld(
            Dimension.OVERWORLD,
            0.0, 0.0, 0.0,
            0f, 0f
        )

        limboAuthServer = factory
            .createLimbo(authWorld)
            .setName("HyperzoneLogin")
            .setWorldTime(1000L)
            .setGameMode(GameMode.ADVENTURE)
    }

    // 依靠GameProfileRequestEvent，到这里我们的验证早就结束了，这里的onlineMode应该是正确的
    @Subscribe
    fun onLoginLimboRegister(event: LoginLimboRegisterEvent) {
        // 必须callBack
        event.addOnJoinCallback { authPlayer(event.player) }
    }

    fun authPlayer(player: Player) {
        val hyperZonePlayer = HyperZonePlayerManager.getByPlayer(player)

        val limboAuthStartEvent = LimboAuthStartEvent(player, hyperZonePlayer)
        HyperZoneLoginMain.getInstance().proxy.eventManager.fire(limboAuthStartEvent).join()
        if (limboAuthStartEvent.pass) {
            factory.passLoginLimbo(player)
            return
        }

        val newHandler = LimboAuthSessionHandler(player, hyperZonePlayer)
        authServer.spawnPlayer(player, newHandler)
    }
}
