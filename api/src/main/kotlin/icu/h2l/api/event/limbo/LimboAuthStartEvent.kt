package icu.h2l.api.event.limbo

import com.velocitypowered.api.proxy.Player
import icu.h2l.api.player.HyperZonePlayer

class LimboAuthStartEvent(
    val proxyPlayer: Player,
    val hyperZonePlayer: HyperZonePlayer
) {
    var pass: Boolean = false
}
