package icu.h2l.login.limbo.command

import icu.h2l.login.limbo.command.commands.*

/**
 * Limbo命令初始化器
 * 负责注册所有内置命令
 */
object LimboCommandInitializer {
    
    /**
     * 注册所有内置命令
     */
    fun registerDefaultCommands() {
        LimboCommandManager.registerCommands(
            HelpCommand(),
            LoginCommand(),
            ExitCommand(),
            InfoCommand()
        )
    }
    
    /**
     * 使用DSL方式注册示例命令
     */
    fun registerExampleDSLCommands() {
        // 示例：使用DSL方式创建一个简单的ping命令
        LimboCommandManager.command("ping") {
            description("测试命令")
            aliases("pong")
            usage("ping")
            
            execute { handler, player, _ ->
                handler.sendMessage(
                    net.kyori.adventure.text.Component.text(
                        "Pong! 你好, ${player.username}!",
                        net.kyori.adventure.text.format.NamedTextColor.GREEN
                    )
                )
                true
            }
        }
        
        // 示例：创建一个需要权限的命令  
        LimboCommandManager.command("admin") {
            description("管理员命令")
            usage("admin")
            permission("hyperzonelogin.admin")
            
            execute { handler, player, args ->
                handler.sendMessage(
                    net.kyori.adventure.text.Component.text(
                        "你是管理员！",
                        net.kyori.adventure.text.format.NamedTextColor.RED
                    )
                )
                true
            }
        }
        
        // 示例：创建一个带参数的命令
        LimboCommandManager.command("echo") {
            description("回显消息")
            usage("echo <消息>")
            aliases("say")
            
            execute { handler, player, args ->
                if (args.isEmpty()) {
                    handler.sendMessage(
                        net.kyori.adventure.text.Component.text(
                            "请提供要回显的消息",
                            net.kyori.adventure.text.format.NamedTextColor.RED
                        )
                    )
                    false
                } else {
                    val message = args.joinToString(" ")
                    handler.sendMessage(
                        net.kyori.adventure.text.Component.text(
                            "回显: $message",
                            net.kyori.adventure.text.format.NamedTextColor.AQUA
                        )
                    )
                    true
                }
            }
        }
    }
}
