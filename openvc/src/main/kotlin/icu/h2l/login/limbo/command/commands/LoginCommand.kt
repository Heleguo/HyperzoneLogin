package icu.h2l.login.limbo.command.commands

import com.velocitypowered.api.proxy.Player
import icu.h2l.login.HyperZoneLoginMain
import icu.h2l.login.limbo.command.LimboCommand
import icu.h2l.login.limbo.handler.LimboAuthSessionHandler
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

/**
 * 登录命令
 * 用于触发Yggdrasil验证流程
 */
class LoginCommand : LimboCommand {
    override val name: String = "login"
    override val aliases: List<String> = listOf("l", "登录")
    override val description: String = "开始登录验证流程"
    override val usage: String = "login"
    
    override fun execute(handler: LimboAuthSessionHandler, player: Player, args: Array<String>): Boolean {
        handler.sendMessage(Component.text("正在开始验证流程...", NamedTextColor.YELLOW))
        
        // 触发验证
        val authManager = HyperZoneLoginMain.getInstance().authManager
        authManager.startYggdrasilAuth(player.username, player.uniqueId, player.remoteAddress.address.hostAddress)
        
        return true
    }
}
