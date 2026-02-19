package icu.h2l.login.auth.online

import com.velocitypowered.api.event.Subscribe
import icu.h2l.api.event.connection.OnlineAuthEvent
import icu.h2l.api.event.limbo.LimboSpawnEvent
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class YggdrasilEventListener(
    private val yggdrasilAuthModule: YggdrasilAuthModule
) {
    private data class PendingAuthContext(
        val userName: String,
        val uuid: UUID,
        val serverId: String,
        val playerIp: String?
    )

    private val pendingAuthContext = ConcurrentHashMap<String, PendingAuthContext>()

    @Subscribe
    fun onOnlineAuth(event: OnlineAuthEvent) {
        if (!event.isOnline) return;
        pendingAuthContext[event.userName] = PendingAuthContext(
            userName = event.userName,
            uuid = event.userUUID,
            serverId = event.serverId,
            playerIp = event.playerIp
        )
    }

    @Subscribe
    fun onLimboSpawn(event: LimboSpawnEvent) {
        val username = event.proxyPlayer.username

        yggdrasilAuthModule.registerLimboHandler(username, event.sessionOverVerify)

        val cached = pendingAuthContext.remove(username)
        val fallbackIp = (event.proxyPlayer.remoteAddress as? InetSocketAddress)?.hostString

        yggdrasilAuthModule.startYggdrasilAuth(
            username = username,
            uuid = cached?.uuid ?: event.proxyPlayer.uniqueId,
            serverId = cached?.serverId ?: "",
            playerIp = cached?.playerIp ?: fallbackIp
        )
    }
}
