package icu.h2l.login.limbo.command.commands

import com.velocitypowered.api.proxy.Player
import icu.h2l.login.limbo.command.LimboCommand
import icu.h2l.login.limbo.handler.LimboAuthSessionHandler
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

/**
 * 帮助命令
 * 显示所有可用的命令列表
 */
class HelpCommand : LimboCommand {
    override val name: String = "help"
    override val aliases: List<String> = listOf("?", "h")
    override val description: String = "显示所有可用的命令"
    override val usage: String = "help [命令名称]"
    
    override fun execute(handler: LimboAuthSessionHandler, player: Player, args: Array<String>): Boolean {
        if (args.isEmpty()) {
            // 显示所有命令
            handler.sendMessage(Component.text("=== 可用命令 ===", NamedTextColor.GOLD))
            
            val commands = icu.h2l.login.limbo.command.LimboCommandManager.getAllCommands()
            commands.forEach { command ->
                handler.sendMessage(
                    Component.text("${command.name}", NamedTextColor.YELLOW)
                        .append(Component.text(" - ${command.description}", NamedTextColor.GRAY))
                )
            }
        } else {
            // 显示特定命令的详细信息
            val commandName = args[0]
            val command = icu.h2l.login.limbo.command.LimboCommandManager.getCommand(commandName)
            
            if (command != null) {
                handler.sendMessage(Component.text("=== 命令: ${command.name} ===", NamedTextColor.GOLD))
                handler.sendMessage(Component.text("描述: ${command.description}", NamedTextColor.GRAY))
                handler.sendMessage(Component.text("用法: ${command.usage}", NamedTextColor.YELLOW))
                
                if (command.aliases.isNotEmpty()) {
                    handler.sendMessage(
                        Component.text("别名: ${command.aliases.joinToString(", ")}", NamedTextColor.GRAY)
                    )
                }
            } else {
                handler.sendMessage(Component.text("未找到命令: $commandName", NamedTextColor.RED))
            }
        }
        
        return true
    }
}
