package icu.h2l.login.manager

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.proxy.Player
import icu.h2l.api.player.HyperZonePlayer
import icu.h2l.api.player.HyperZonePlayerAccessor
import icu.h2l.api.player.getChannel
import icu.h2l.login.player.OpenVcHyperZonePlayer
import io.netty.channel.Channel
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object HyperZonePlayerManager : HyperZonePlayerAccessor {
    private val playersByPlayer = ConcurrentHashMap<Channel, OpenVcHyperZonePlayer>()

    override fun create(channel: Channel, userName: String, uuid: UUID): HyperZonePlayer {
        return playersByPlayer.computeIfAbsent(channel) {
            OpenVcHyperZonePlayer(userName,uuid)
        }
    }

    override fun getByPlayer(player: Player): HyperZonePlayer {
       return getByChannel(player.getChannel())
    }

    override fun getByChannel(channel: Channel): HyperZonePlayer {
        return playersByPlayer[channel]!!
    }

    fun remove(player: Player) {
        playersByPlayer.remove(player.getChannel())
    }

    @Subscribe
    fun onDisconnect(event: DisconnectEvent) {
        val player = event.player
        remove(player)
    }
}
