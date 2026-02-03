package icu.h2l.login.limbo.command.commands

import com.velocitypowered.api.proxy.Player
import icu.h2l.login.limbo.command.LimboCommand
import icu.h2l.login.limbo.handler.LimboAuthSessionHandler
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

/**
 * 退出命令
 * 断开玩家连接
 */
class ExitCommand : LimboCommand {
    override val name: String = "exit"
    override val aliases: List<String> = listOf("quit", "logout", "退出")
    override val description: String = "退出游戏"
    override val usage: String = "exit"
    
    override fun execute(handler: LimboAuthSessionHandler, player: Player, args: Array<String>): Boolean {
        player.disconnect(Component.text("再见！", NamedTextColor.YELLOW))
        return true
    }
}
