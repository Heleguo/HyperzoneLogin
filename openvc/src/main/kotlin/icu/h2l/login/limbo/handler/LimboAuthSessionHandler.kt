package icu.h2l.login.limbo.handler

import com.velocitypowered.api.proxy.Player
import icu.h2l.login.limbo.command.LimboCommandHandler
import icu.h2l.login.limbo.command.LimboCommandManager
import net.elytrium.limboapi.api.Limbo
import net.elytrium.limboapi.api.LimboSessionHandler
import net.elytrium.limboapi.api.player.LimboPlayer
import net.kyori.adventure.text.Component
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class LimboAuthSessionHandler(private val proxyPlayer: Player) : LimboSessionHandler {
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

    /**
     * 命令处理器（可以是全局的或者为此会话自定义的）
     */
    private var commandHandler: LimboCommandHandler = LimboCommandManager.getHandler()

    /**
     * 设置自定义的命令处理器
     * 如果不设置，则使用全局的命令管理器
     */
    fun setCommandHandler(handler: LimboCommandHandler) {
        this.commandHandler = handler
    }

    /**
     * 获取当前使用的命令处理器
     */
    fun getCommandHandler(): LimboCommandHandler {
        return commandHandler
    }

    override fun onSpawn(server: Limbo, player: LimboPlayer) {
        this.player = player
        this.player.disableFalling()

        // 标记玩家已spawn
        hasSpawned.set(true)

        // 发送消息队列中的所有消息
        flushMessageQueue()
    }

    override fun onChat(message: String) {
        // 使用命令处理器处理消息
        val handled = commandHandler.handleMessage(this, proxyPlayer, message)
        
        // 如果消息没有被命令系统处理，可以在这里添加其他逻辑
        if (!handled) {
            // 例如：显示提示信息
            proxyPlayer.sendPlainMessage("未知命令: $message")
            proxyPlayer.sendPlainMessage("输入 'help' 查看可用命令")
        }
    }

    /**
     * 完成验证，结束Limbo状态
     * 此方法由AuthManager在Yggdrasil验证成功时调用
     */
    fun overVerify() {
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
    fun isOverVerified(): Boolean {
        return isOverVerified.get()
    }

    /**
     * 发送消息给玩家
     * 如果玩家已spawn，直接发送；否则放入消息队列
     * 
     * @param message 要发送的消息
     */
    fun sendMessage(message: Component) {
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
