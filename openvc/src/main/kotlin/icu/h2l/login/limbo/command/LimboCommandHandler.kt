package icu.h2l.login.limbo.command

import com.velocitypowered.api.proxy.Player
import icu.h2l.login.limbo.handler.LimboAuthSessionHandler
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import java.util.concurrent.ConcurrentHashMap

/**
 * Limbo命令处理器
 * 负责处理玩家在Limbo中发送的消息，解析并执行命令
 */
class LimboCommandHandler {
    /**
     * 注册的命令映射
     * Key: 命令名称或别名（小写）
     * Value: 命令实例
     */
    private val commands = ConcurrentHashMap<String, LimboCommand>()
    
    /**
     * 命令前缀（可选），默认为空表示不需要前缀
     */
    var commandPrefix: String = ""
    
    /**
     * 是否启用严格模式（必须要有前缀）
     */
    var strictMode: Boolean = false
    
    /**
     * 未知命令时的处理器（可选）
     * 如果设置了此处理器，未匹配到命令时会调用它
     */
    var unknownCommandHandler: ((LimboAuthSessionHandler, Player, String) -> Unit)? = null
    
    /**
     * 注册命令
     * 
     * @param command 命令实例
     */
    fun registerCommand(command: LimboCommand) {
        // 注册主命令名
        commands[command.name.lowercase()] = command
        
        // 注册所有别名
        command.aliases.forEach { alias ->
            commands[alias.lowercase()] = command
        }
    }
    
    /**
     * 批量注册命令
     * 
     * @param commands 命令列表
     */
    fun registerCommands(vararg commands: LimboCommand) {
        commands.forEach { registerCommand(it) }
    }
    
    /**
     * 取消注册命令
     * 
     * @param commandName 命令名称
     */
    fun unregisterCommand(commandName: String) {
        val command = commands.remove(commandName.lowercase())
        
        // 同时移除所有别名
        command?.aliases?.forEach { alias ->
            commands.remove(alias.lowercase())
        }
    }
    
    /**
     * 获取已注册的命令
     * 
     * @param commandName 命令名称或别名
     * @return 命令实例，如果未找到则返回null
     */
    fun getCommand(commandName: String): LimboCommand? {
        return commands[commandName.lowercase()]
    }
    
    /**
     * 获取所有已注册的命令
     * 
     * @return 命令集合（去重）
     */
    fun getAllCommands(): Collection<LimboCommand> {
        return commands.values.distinct()
    }
    
    /**
     * 处理玩家发送的消息
     * 
     * @param handler Limbo会话处理器
     * @param player 玩家
     * @param message 玩家发送的消息
     * @return 是否成功处理（如果消息是命令则返回true，否则返回false）
     */
    fun handleMessage(handler: LimboAuthSessionHandler, player: Player, message: String): Boolean {
        var msg = message.trim()
        if (msg.isEmpty()) {
            return false
        }
        
        // 检查是否有命令前缀
        if (commandPrefix.isNotEmpty()) {
            if (msg.startsWith(commandPrefix)) {
                msg = msg.substring(commandPrefix.length)
            } else if (strictMode) {
                // 严格模式下，没有前缀的消息不被处理
                return false
            }
        }
        
        // 解析命令和参数
        val parts = msg.split(" ", limit = 2)
        val commandName = parts[0].lowercase()
        val args = if (parts.size > 1) {
            parseArgs(parts[1])
        } else {
            emptyArray()
        }
        
        // 查找命令
        val command = commands[commandName]
        
        if (command != null) {
            // 检查权限
            if (!command.hasPermission(player)) {
                handler.sendMessage(
                    Component.text("你没有权限执行此命令", NamedTextColor.RED)
                )
                return true
            }
            
            // 执行命令
            try {
                val success = command.execute(handler, player, args)
                if (!success) {
                    // 命令执行失败，显示用法
                    handler.sendMessage(
                        Component.text("用法: ${command.usage}", NamedTextColor.YELLOW)
                    )
                }
            } catch (e: Exception) {
                handler.sendMessage(
                    Component.text("执行命令时发生错误: ${e.message}", NamedTextColor.RED)
                )
                e.printStackTrace()
            }
            
            return true
        } else {
            // 未找到命令
            if (unknownCommandHandler != null) {
                unknownCommandHandler?.invoke(handler, player, message)
                return true
            }
            
            // 如果没有设置未知命令处理器，且不是严格模式，则返回false表示未处理
            return strictMode
        }
    }
    
    /**
     * 解析命令参数
     * 支持引号包裹的参数（包含空格）
     * 
     * @param argsString 参数字符串
     * @return 参数数组
     */
    private fun parseArgs(argsString: String): Array<String> {
        val args = mutableListOf<String>()
        val currentArg = StringBuilder()
        var inQuotes = false
        var escapeNext = false
        
        for (char in argsString) {
            when {
                escapeNext -> {
                    currentArg.append(char)
                    escapeNext = false
                }
                char == '\\' -> {
                    escapeNext = true
                }
                char == '"' -> {
                    inQuotes = !inQuotes
                }
                char == ' ' && !inQuotes -> {
                    if (currentArg.isNotEmpty()) {
                        args.add(currentArg.toString())
                        currentArg.clear()
                    }
                }
                else -> {
                    currentArg.append(char)
                }
            }
        }
        
        if (currentArg.isNotEmpty()) {
            args.add(currentArg.toString())
        }
        
        return args.toTypedArray()
    }
    
    /**
     * 清除所有命令
     */
    fun clearCommands() {
        commands.clear()
    }
}
