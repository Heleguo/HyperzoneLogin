package icu.h2l.login.player

import com.velocitypowered.api.proxy.Player
import icu.h2l.api.db.Profile
import icu.h2l.api.player.HyperZonePlayer
import icu.h2l.login.HyperZoneLoginMain
import icu.h2l.login.util.RemapUtils
import net.elytrium.limboapi.api.player.LimboPlayer
import net.kyori.adventure.text.Component
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class OpenVcHyperZonePlayer(
    private val proxyPlayer: Player
) : HyperZonePlayer {
    @Volatile
    var profileId: UUID? = null

    private val isVerifiedState = AtomicBoolean(false)
    private val hasSpawned = AtomicBoolean(false)
    private val messageQueue = ConcurrentLinkedQueue<Component>()

    @Volatile
    private var limboPlayer: LimboPlayer? = null

    private val databaseHelper = HyperZoneLoginMain.getInstance().databaseHelper

    init {
        profileId = databaseHelper
            .getProfileByNameOrUuid(proxyPlayer.username, proxyPlayer.uniqueId)
            ?.id
    }

    fun onSpawn(player: LimboPlayer) {
        limboPlayer = player
        hasSpawned.set(true)

        while (messageQueue.isNotEmpty()) {
            val message = messageQueue.poll() ?: continue
            proxyPlayer.sendMessage(message)
        }
    }

    override fun canRegister(): Boolean {
        return profileId == null
    }

    override fun register(userName: String?, uuid: UUID?): Profile {
        val resolvedName = userName ?: proxyPlayer.username
        val remapPrefix = HyperZoneLoginMain.getRemapConfig().prefix
        val resolvedUuid = uuid ?: RemapUtils.genUUID(resolvedName, remapPrefix)

        val existing = databaseHelper.getProfileByNameOrUuid(resolvedName, resolvedUuid)
        if (existing != null) {
            throw IllegalStateException("玩家 $resolvedName 已存在 Profile，无法重复注册")
        }

        val profile = Profile(
            id = RemapUtils.genUUID(resolvedName, "h2l"),
            name = resolvedName,
            uuid = resolvedUuid
        )

        val created = databaseHelper.createProfile(profile.id, profile.name, profile.uuid)
        if (!created) {
            throw IllegalStateException("玩家 ${proxyPlayer.username} 注册失败，数据库写入失败")
        }

        profileId = profile.id

        return profile
    }

    override fun getProfile(): Profile? {
        val currentProfileId = profileId ?: return null
        return databaseHelper.getProfile(currentProfileId)
    }

    override fun isVerified(): Boolean {
        return isVerifiedState.get()
    }

    override fun canBind(): Boolean {
        return isVerified()
    }

    override fun overVerify() {
        if (isVerifiedState.compareAndSet(false, true)) {
            limboPlayer?.disconnect()
        }
    }

    fun exitLimbo() {
        limboPlayer?.disconnect()
    }

    override fun sendMessage(message: Component) {
        if (hasSpawned.get()) {
            proxyPlayer.sendMessage(message)
            return
        }

        messageQueue.offer(message)
    }
}
