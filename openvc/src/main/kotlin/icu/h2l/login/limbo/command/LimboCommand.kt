package icu.h2l.login.limbo.command

import com.velocitypowered.api.proxy.Player
import icu.h2l.login.limbo.handler.LimboAuthSessionHandler

/**
 * Limbo命令接口
 * 所有在Limbo中执行的命令都应该实现这个接口
 */
interface LimboCommand {
    /**
     * 命令名称（无需前缀）
     */
    val name: String
    
    /**
     * 命令别名
     */
    val aliases: List<String>
        get() = emptyList()
    
    /**
     * 命令描述
     */
    val description: String
        get() = ""
    
    /**
     * 命令使用方法
     */
    val usage: String
        get() = name
    
    /**
     * 执行命令
     * 
     * @param handler Limbo会话处理器
     * @param player 执行命令的玩家
     * @param args 命令参数
     * @return 命令是否执行成功
     */
    fun execute(handler: LimboAuthSessionHandler, player: Player, args: Array<String>): Boolean
    
    /**
     * 检查玩家是否有权限执行此命令
     * 
     * @param player 玩家
     * @return 是否有权限
     */
    fun hasPermission(player: Player): Boolean {
        return true // 默认所有人都可以执行
    }
    
    /**
     * 提供Tab补全建议
     * 
     * @param player 玩家
     * @param args 当前已输入的参数
     * @return 补全建议列表
     */
    fun onTabComplete(player: Player, args: Array<String>): List<String> {
        return emptyList()
    }
}
