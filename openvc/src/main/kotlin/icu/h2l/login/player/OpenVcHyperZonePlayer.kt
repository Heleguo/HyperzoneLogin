package icu.h2l.login.player

import com.velocitypowered.api.proxy.Player
import icu.h2l.api.player.HyperZonePlayer
import icu.h2l.login.HyperZoneLoginMain
import net.elytrium.limboapi.api.player.LimboPlayer
import net.kyori.adventure.text.Component
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class OpenVcHyperZonePlayer(
    private val proxyPlayer: Player
) : HyperZonePlayer {
    private val isVerifiedState = AtomicBoolean(false)
    private val hasSpawned = AtomicBoolean(false)
    private val messageQueue = ConcurrentLinkedQueue<Component>()

    @Volatile
    private var limboPlayer: LimboPlayer? = null

    fun onSpawn(player: LimboPlayer) {
        limboPlayer = player
        hasSpawned.set(true)

        while (messageQueue.isNotEmpty()) {
            val message = messageQueue.poll() ?: continue
            proxyPlayer.sendMessage(message)
        }
    }

    override fun canRegister(): Boolean {
        val databaseHelper = HyperZoneLoginMain.getInstance().databaseHelper
        val profile = databaseHelper.getProfileByNameOrUuid(proxyPlayer.username, proxyPlayer.uniqueId)
        return profile == null
    }

    override fun isVerified(): Boolean {
        return isVerifiedState.get()
    }

    override fun overVerify() {
        if (isVerifiedState.compareAndSet(false, true)) {
            limboPlayer?.disconnect()
        }
    }

    override fun sendMessage(message: Component) {
        if (hasSpawned.get()) {
            proxyPlayer.sendMessage(message)
            return
        }

        messageQueue.offer(message)
    }
}
