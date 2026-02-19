package icu.h2l.login.limbo

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import icu.h2l.login.HyperZoneLoginMain
import icu.h2l.login.limbo.handler.LimboAuthSessionHandler
import net.elytrium.limboapi.api.Limbo
import net.elytrium.limboapi.api.LimboFactory
import net.elytrium.limboapi.api.chunk.Dimension
import net.elytrium.limboapi.api.chunk.VirtualWorld
import net.elytrium.limboapi.api.event.LoginLimboRegisterEvent
import net.elytrium.limboapi.api.player.GameMode
//用于注册到limbo服务器，不需要融合于任何模块目前
class LimboAuth(server: ProxyServer) {
    private val factory: LimboFactory
    private lateinit var authServer: Limbo

    init {
        factory = server.pluginManager.getPlugin("limboapi")
            .flatMap { obj: PluginContainer -> obj.instance }
            .orElseThrow() as LimboFactory
    }

    fun load() {
        val authWorld: VirtualWorld = factory.createVirtualWorld(
            Dimension.THE_END,
            0.0, 0.0, 0.0,
            0f, 0f
        )

        authServer = factory
            .createLimbo(authWorld)
            .setName("HyperzoneLogin")
            .setWorldTime(1000L)
            .setGameMode(GameMode.ADVENTURE)
    }

    // 依靠GameProfileRequestEvent，到这里我们的验证早就结束了，这里的onlineMode应该是正确的
    @Subscribe
    fun onLoginLimboRegister(event: LoginLimboRegisterEvent) {
        // 在线额外特判
        if (event.player.isOnlineMode) {
            val authManager = HyperZoneLoginMain.getInstance().yggdrasilAuthModule
            val handler = authManager.getLimboHandler(event.player.username)

            // 如果handler已经完成over验证，直接跳过登入流程
            if (handler != null && handler.isOverVerified()) {
                return
            }
        }

        // 必须callBack
        event.addOnJoinCallback { authPlayer(event.player) }
    }

    fun authPlayer(player: Player) {
        // this.factory.passLoginLimbo(player)
        val authManager = HyperZoneLoginMain.getInstance().yggdrasilAuthModule
        val handler = authManager.getLimboHandler(player.username)

        if (handler != null) {
            // 从AuthManager中获取已注册的handler
            authServer.spawnPlayer(player, handler)
        } else {
            // 如果没有找到，创建新的handler并注册
            val newHandler = LimboAuthSessionHandler(player)
            authManager.registerLimboHandler(player.username, newHandler)
            authServer.spawnPlayer(player, newHandler)
        }
    }
}
