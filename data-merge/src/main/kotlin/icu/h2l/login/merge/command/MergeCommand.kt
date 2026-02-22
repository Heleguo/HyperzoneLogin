package icu.h2l.login.merge.command

import com.velocitypowered.api.command.SimpleCommand
import java.util.concurrent.atomic.AtomicBoolean

class MergeCommand(
    private val runMlMigration: () -> String,
    private val runAmMigration: () -> String
) : SimpleCommand {
    private val running = AtomicBoolean(false)

    override fun execute(invocation: SimpleCommand.Invocation) {
        val sender = invocation.source()
        val args = invocation.arguments()

        if (args.isEmpty()) {
            sender.sendPlainMessage("§e/hzl-merge ml")
            sender.sendPlainMessage("§e/hzl-merge am")
            return
        }

        val subCommand = args[0].lowercase()
        if (subCommand != "ml" && subCommand != "am") {
            sender.sendPlainMessage("§c未知子命令: ${args[0]}")
            sender.sendPlainMessage("§e可用子命令: ml, am")
            return
        }

        if (!running.compareAndSet(false, true)) {
            sender.sendPlainMessage("§c迁移正在执行中，请稍后再试")
            return
        }

        try {
            val summary = when (subCommand) {
                "ml" -> {
                    sender.sendPlainMessage("§e开始执行 ML 迁移，请稍候...")
                    runMlMigration()
                }

                else -> {
                    sender.sendPlainMessage("§e开始执行 AUTHME 迁移，请稍候...")
                    runAmMigration()
                }
            }

            sender.sendPlainMessage("§a迁移完成: $summary")
            sender.sendPlainMessage(
                "§a详细日志已输出到 ${if (subCommand == "ml") "merge-ml.log" else "merge-am.log"}"
            )
        } catch (ex: Exception) {
            sender.sendPlainMessage("§c迁移失败: ${ex.message}")
        } finally {
            running.set(false)
        }
    }

    override fun hasPermission(invocation: SimpleCommand.Invocation): Boolean {
        return invocation.source().hasPermission("hyperzonelogin.admin")
    }
}
