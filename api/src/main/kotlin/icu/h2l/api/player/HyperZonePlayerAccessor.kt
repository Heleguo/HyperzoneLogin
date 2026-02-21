package icu.h2l.api.player

import com.velocitypowered.api.proxy.Player
import io.netty.channel.Channel
import java.util.UUID

interface HyperZonePlayerAccessor {
    fun create(channel: Channel, userName: String, uuid: UUID): HyperZonePlayer
    fun getByPlayer(player: Player): HyperZonePlayer
    fun getByChannel(channel: Channel): HyperZonePlayer
}

interface HyperZonePlayerAccessorProvider {
    val hyperZonePlayers: HyperZonePlayerAccessor
}
