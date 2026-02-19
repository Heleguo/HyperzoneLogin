package icu.h2l.login.auth.online

import com.velocitypowered.api.event.Subscribe
import icu.h2l.api.event.connection.OnlineAuthEvent
import icu.h2l.api.event.limbo.LimboSpawnEvent
import icu.h2l.api.log.debug

class YggdrasilEventListener(
    private val yggdrasilAuthModule: YggdrasilAuthModule
) {
    @Subscribe
    fun onOnlineAuth(event: OnlineAuthEvent) {
        if (!event.isOnline) return

        debug { "[YggdrasilFlow] OnlineAuthEvent 收到，开始验证: user=${event.userName}, uuid=${event.userUUID}" }

        yggdrasilAuthModule.startYggdrasilAuth(
            username = event.userName,
            uuid = event.userUUID,
            serverId = event.serverId,
            playerIp = event.playerIp
        )
    }

    @Subscribe
    fun onLimboSpawn(event: LimboSpawnEvent) {
        val username = event.proxyPlayer.username
        debug { "[YggdrasilFlow] LimboSpawnEvent 收到，注册回调: user=$username" }
        yggdrasilAuthModule.registerLimboHandler(username, event.sessionOverVerify)
    }
}
