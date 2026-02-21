package icu.h2l.api.event.connection

import com.velocitypowered.api.event.annotation.AwaitingEvent
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.util.GameProfile

/**
 * 在登入阶段请求/改写玩家 GameProfile 时触发。
 *
 * Velocity typically fires this event asynchronously and does not wait for a response. However,
 * it will wait for all [DisconnectEvent]s for every player on the proxy to fire
 * successfully before the proxy shuts down. This event is the sole exception to the
 * [AwaitingEvent] contract.
 */
@AwaitingEvent
class HyperZoneGameProfileRequestEvent(
    val player: Player,
    val originalProfile: GameProfile,
    val isOnline: Boolean,
) {
    var gameProfile: GameProfile? = null

    fun getResultProfile(): GameProfile {
        return gameProfile ?: originalProfile
    }
}
