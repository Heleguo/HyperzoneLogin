package icu.h2l.api.player

import com.velocitypowered.api.proxy.Player

interface HyperZonePlayerAccessor {
    fun getOrCreate(proxyPlayer: Player): HyperZonePlayer
    fun getByPlayer(player: Player): HyperZonePlayer?
}

interface HyperZonePlayerAccessorProvider {
    val hyperZonePlayers: HyperZonePlayerAccessor
}
