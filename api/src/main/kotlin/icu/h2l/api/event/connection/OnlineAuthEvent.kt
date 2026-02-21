package icu.h2l.api.event.connection

import com.velocitypowered.api.event.annotation.AwaitingEvent
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.util.GameProfile
import java.net.SocketAddress
import java.util.UUID

/**
 * 进行正版验证类似操作时触发.
 *
 * Velocity typically fires this event asynchronously and does not wait for a response. However,
 * it will wait for all [DisconnectEvent]s for every player on the proxy to fire
 * successfully before the proxy shuts down. This event is the sole exception to the
 * [AwaitingEvent] contract.
 */
@AwaitingEvent
class OnlineAuthEvent(
    val userName: String,
    val userUUID: UUID,
    val serverId: String,
    val playerIp: String,
    val remoteAddress: SocketAddress,
    val isOnline: Boolean
) {
    var gameProfile: GameProfile? = null
    var player: Player? = null
}
