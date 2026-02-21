package icu.h2l.login.manager

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.proxy.Player
import icu.h2l.api.player.HyperZonePlayerAccessor
import icu.h2l.login.player.OpenVcHyperZonePlayer
import java.util.concurrent.ConcurrentHashMap

object HyperZonePlayerManager : HyperZonePlayerAccessor {
    private val playersByPlayer = ConcurrentHashMap<Player, OpenVcHyperZonePlayer>()

    override fun getOrCreate(proxyPlayer: Player): OpenVcHyperZonePlayer {
        return playersByPlayer.computeIfAbsent(proxyPlayer) {
            OpenVcHyperZonePlayer(proxyPlayer)
        }
    }

    override fun getByPlayer(player: Player): OpenVcHyperZonePlayer? {
        return playersByPlayer[player]
    }

    fun remove(player: Player) {
        playersByPlayer.remove(player)
    }

    @Subscribe
    fun onDisconnect(event: DisconnectEvent) {
        val player = event.player
        remove(player)
    }
}
