package icu.h2l.login.limbo.command.commands

import com.velocitypowered.api.proxy.Player
import icu.h2l.login.limbo.command.LimboCommand
import icu.h2l.login.limbo.handler.LimboAuthSessionHandler
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

/**
 * 信息命令
 * 显示玩家的相关信息
 */
class InfoCommand : LimboCommand {
    override val name: String = "info"
    override val aliases: List<String> = listOf("information", "me", "信息")
    override val description: String = "显示你的信息"
    override val usage: String = "info"
    
    override fun execute(handler: LimboAuthSessionHandler, player: Player, args: Array<String>): Boolean {
        handler.sendMessage(Component.text("=== 玩家信息 ===", NamedTextColor.GOLD))
        handler.sendMessage(Component.text("用户名: ${player.username}", NamedTextColor.YELLOW))
        handler.sendMessage(Component.text("UUID: ${player.uniqueId}", NamedTextColor.YELLOW))
        handler.sendMessage(Component.text("IP地址: ${player.remoteAddress.address.hostAddress}", NamedTextColor.YELLOW))
        handler.sendMessage(Component.text("在线模式: ${player.isOnlineMode}", NamedTextColor.YELLOW))
        
        return true
    }
}
