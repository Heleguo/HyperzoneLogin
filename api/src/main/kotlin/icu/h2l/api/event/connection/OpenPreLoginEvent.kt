package icu.h2l.api.event.connection

import com.velocitypowered.api.event.annotation.AwaitingEvent
import com.velocitypowered.api.event.connection.DisconnectEvent
import io.netty.channel.Channel
import java.util.UUID

/**
 * 触发PreLogin后触发，用于鉴别是否为离线玩家以及进行离线的验证.
 *
 * Velocity typically fires this event asynchronously and does not wait for a response. However,
 * it will wait for all [DisconnectEvent]s for every player on the proxy to fire
 * successfully before the proxy shuts down. This event is the sole exception to the
 * [AwaitingEvent] contract.
 */
@AwaitingEvent
class OpenPreLoginEvent(
    val uuid: UUID,
    val userName: String,
    val host: String,
    val channel: Channel
) {
    var isOnline: Boolean = true
}
