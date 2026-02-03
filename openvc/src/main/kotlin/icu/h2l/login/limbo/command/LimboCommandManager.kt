package icu.h2l.login.limbo.command

import com.velocitypowered.api.proxy.Player

/**
 * Limbo命令管理器
 * 提供全局的命令注册和管理功能
 * 可以与 Mojang Brigadier 等外部系统集成
 */
object LimboCommandManager {
    /**
     * 全局命令处理器实例
     */
    private val globalHandler = LimboCommandHandler()
    
    /**
     * 命令注册监听器列表
     */
    private val registrationListeners = mutableListOf<CommandRegistrationListener>()
    
    /**
     * 命令注册监听器接口
     */
    interface CommandRegistrationListener {
        /**
         * 当命令被注册时调用
         */
        fun onCommandRegistered(command: LimboCommand)
        
        /**
         * 当命令被取消注册时调用
         */
        fun onCommandUnregistered(commandName: String)
    }
    
    /**
     * 获取全局命令处理器
     */
    fun getHandler(): LimboCommandHandler {
        return globalHandler
    }
    
    /**
     * 注册命令
     * 
     * @param command 命令实例
     */
    fun registerCommand(command: LimboCommand) {
        globalHandler.registerCommand(command)
        
        // 通知所有监听器
        registrationListeners.forEach { 
            it.onCommandRegistered(command)
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
        globalHandler.unregisterCommand(commandName)
        
        // 通知所有监听器
        registrationListeners.forEach {
            it.onCommandUnregistered(commandName)
        }
    }
    
    /**
     * 获取命令
     * 
     * @param commandName 命令名称或别名
     * @return 命令实例，如果未找到则返回null
     */
    fun getCommand(commandName: String): LimboCommand? {
        return globalHandler.getCommand(commandName)
    }
    
    /**
     * 获取所有已注册的命令
     * 
     * @return 命令集合
     */
    fun getAllCommands(): Collection<LimboCommand> {
        return globalHandler.getAllCommands()
    }
    
    /**
     * 检查命令是否已注册
     * 
     * @param commandName 命令名称
     * @return 是否已注册
     */
    fun isCommandRegistered(commandName: String): Boolean {
        return globalHandler.getCommand(commandName) != null
    }
    
    /**
     * 添加命令注册监听器
     * 用于外部系统（如 Mojang Brigadier）监听命令注册事件
     * 
     * @param listener 监听器
     */
    fun addRegistrationListener(listener: CommandRegistrationListener) {
        registrationListeners.add(listener)
    }
    
    /**
     * 移除命令注册监听器
     * 
     * @param listener 监听器
     */
    fun removeRegistrationListener(listener: CommandRegistrationListener) {
        registrationListeners.remove(listener)
    }
    
    /**
     * 设置命令前缀
     * 
     * @param prefix 前缀字符串
     */
    fun setCommandPrefix(prefix: String) {
        globalHandler.commandPrefix = prefix
    }
    
    /**
     * 设置严格模式
     * 
     * @param strict 是否启用严格模式
     */
    fun setStrictMode(strict: Boolean) {
        globalHandler.strictMode = strict
    }
    
    /**
     * 设置未知命令处理器
     * 
     * @param handler 处理器函数
     */
    fun setUnknownCommandHandler(handler: ((icu.h2l.login.limbo.handler.LimboAuthSessionHandler, Player, String) -> Unit)?) {
        globalHandler.unknownCommandHandler = handler
    }
    
    /**
     * 清除所有命令
     */
    fun clearAllCommands() {
        globalHandler.clearCommands()
        registrationListeners.clear()
    }
    
    /**
     * 创建命令构建器
     * 提供DSL风格的命令注册方式
     * 
     * @param name 命令名称
     * @param init 构建器配置函数
     */
    fun command(name: String, init: LimboCommandBuilder.() -> Unit): LimboCommand {
        val builder = LimboCommandBuilder(name)
        builder.init()
        val command = builder.build()
        registerCommand(command)
        return command
    }
}

/**
 * 命令构建器
 * 提供DSL风格的命令创建方式
 */
class LimboCommandBuilder(private val commandName: String) {
    private var commandDescription: String = ""
    private var commandUsage: String = commandName
    private var commandAliases: List<String> = emptyList()
    private var permissionCheck: (Player) -> Boolean = { true }
    private var executeHandler: ((icu.h2l.login.limbo.handler.LimboAuthSessionHandler, Player, Array<String>) -> Boolean)? = null
    private var tabCompleteHandler: (Player, Array<String>) -> List<String> = { _, _ -> emptyList() }
    
    /**
     * 设置命令描述
     */
    fun description(desc: String) {
        commandDescription = desc
    }
    
    /**
     * 设置命令用法
     */
    fun usage(usage: String) {
        commandUsage = usage
    }
    
    /**
     * 设置命令别名
     */
    fun aliases(vararg aliases: String) {
        commandAliases = aliases.toList()
    }
    
    /**
     * 设置权限检查函数
     */
    fun permission(check: (Player) -> Boolean) {
        permissionCheck = check
    }
    
    /**
     * 设置权限检查（使用权限节点）
     */
    fun permission(node: String) {
        permissionCheck = { player -> player.hasPermission(node) }
    }
    
    /**
     * 设置命令执行处理器
     */
    fun execute(handler: (icu.h2l.login.limbo.handler.LimboAuthSessionHandler, Player, Array<String>) -> Boolean) {
        executeHandler = handler
    }
    
    /**
     * 设置Tab补全处理器
     */
    fun tabComplete(handler: (Player, Array<String>) -> List<String>) {
        tabCompleteHandler = handler
    }
    
    /**
     * 构建命令实例
     */
    fun build(): LimboCommand {
        return object : LimboCommand {
            override val name: String = commandName
            override val description: String = commandDescription
            override val usage: String = commandUsage
            override val aliases: List<String> = commandAliases
            
            override fun execute(
                handler: icu.h2l.login.limbo.handler.LimboAuthSessionHandler,
                player: Player,
                args: Array<String>
            ): Boolean {
                return executeHandler?.invoke(handler, player, args) ?: false
            }
            
            override fun hasPermission(player: Player): Boolean {
                return permissionCheck(player)
            }
            
            override fun onTabComplete(player: Player, args: Array<String>): List<String> {
                return tabCompleteHandler(player, args)
            }
        }
    }
}
