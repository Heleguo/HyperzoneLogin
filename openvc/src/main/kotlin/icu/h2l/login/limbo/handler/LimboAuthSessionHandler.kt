package icu.h2l.login.limbo.handler

import com.velocitypowered.api.proxy.Player
import icu.h2l.api.event.limbo.LimboSpawnEvent
import icu.h2l.api.player.HyperZonePlayer
import icu.h2l.login.HyperZoneLoginMain
import net.elytrium.limboapi.api.Limbo
import net.elytrium.limboapi.api.LimboSessionHandler
import net.elytrium.limboapi.api.player.LimboPlayer
import net.kyori.adventure.text.Component
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class LimboAuthSessionHandler(private val proxyPlayer: Player) : LimboSessionHandler, HyperZonePlayer {
    private lateinit var player: LimboPlayer

    /**
     * 标记玩家是否已经spawn
     */
    private val hasSpawned = AtomicBoolean(false)

    /**
     * 标记是否已经完成over验证
     */
    private val isOverVerified = AtomicBoolean(false)

    /**
     * 消息队列，用于存储spawn前的消息
     */
    private val messageQueue = ConcurrentLinkedQueue<Component>()

    override fun onSpawn(server: Limbo, player: LimboPlayer) {
        this.player = player
        HyperZoneLoginMain.getInstance().proxy.eventManager.fire(
            LimboSpawnEvent(this.player, proxyPlayer, this)
        )
        this.player.disableFalling()

        // 标记玩家已spawn
        hasSpawned.set(true)

        // 发送消息队列中的所有消息
        flushMessageQueue()
    }

    /**
     * 判断是否可以注册。
     *
     * 规则：数据库中不存在该玩家 Profile（按 name 或 uuid）时可注册。
     */
    override fun canRegister(): Boolean {
        val databaseManager = HyperZoneLoginMain.getInstance().databaseManager
        val profileTable = databaseManager.getProfileTable()

        val foundProfile = databaseManager.executeTransaction {
            profileTable.selectAll().where {
                (profileTable.name eq proxyPlayer.username) or (profileTable.uuid eq proxyPlayer.uniqueId)
            }.limit(1).any()
        }

        return !foundProfile
    }

    /**
     * 完成验证，结束Limbo状态
     * 此方法由AuthManager在Yggdrasil验证成功时调用
     */
    override fun overVerify() {
        if (isOverVerified.compareAndSet(false, true)) {
            // 只执行一次
            if (::player.isInitialized) {
                player.disconnect()
            }
        }
    }

    /**
     * 检查是否已经完成over验证
     */
    override fun isVerified(): Boolean {
        return isOverVerified.get()
    }

    /**
     * 发送消息给玩家
     * 如果玩家已spawn，直接发送；否则放入消息队列
     * 
     * @param message 要发送的消息
     */
    override fun sendMessage(message: Component) {
        if (hasSpawned.get() && ::player.isInitialized) {
            // 玩家已spawn，直接发送
            proxyPlayer.sendMessage(message)
        } else {
            // 玩家未spawn，放入消息队列
            messageQueue.offer(message)
        }
    }

    /**
     * 清空消息队列，将所有消息发送给玩家
     */
    private fun flushMessageQueue() {
        while (messageQueue.isNotEmpty()) {
            val message = messageQueue.poll()
            if (message != null && ::player.isInitialized) {
                proxyPlayer.sendMessage(message)
            }
        }
    }
}
