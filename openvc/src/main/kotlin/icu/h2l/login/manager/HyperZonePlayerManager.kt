package icu.h2l.login.manager

import com.velocitypowered.api.proxy.Player
import icu.h2l.login.player.OpenVcHyperZonePlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object HyperZonePlayerManager {
    private val playersByUuid = ConcurrentHashMap<UUID, OpenVcHyperZonePlayer>()
    private val playersByName = ConcurrentHashMap<String, OpenVcHyperZonePlayer>()

    fun getOrCreate(proxyPlayer: Player): OpenVcHyperZonePlayer {
        return playersByUuid.computeIfAbsent(proxyPlayer.uniqueId) {
            OpenVcHyperZonePlayer(proxyPlayer)
        }.also { player ->
            playersByName[proxyPlayer.username.lowercase()] = player
        }
    }

    fun getByUuid(uuid: UUID): OpenVcHyperZonePlayer? {
        return playersByUuid[uuid]
    }

    fun getByName(name: String): OpenVcHyperZonePlayer? {
        return playersByName[name.lowercase()]
    }

    fun getByNameOrUuid(name: String, uuid: UUID): OpenVcHyperZonePlayer? {
        return getByUuid(uuid) ?: getByName(name)
    }

    fun remove(uuid: UUID, name: String? = null) {
        val removed = playersByUuid.remove(uuid)
        if (name != null) {
            playersByName.remove(name.lowercase())
            return
        }

        if (removed != null) {
            playersByName.entries.removeIf { (_, value) -> value === removed }
        }
    }
}
