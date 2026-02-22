package icu.h2l.api.command

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand

data class HyperChatCommandRegistration(
    val name: String,
    val aliases: Set<String> = emptySet(),
    val command: SimpleCommand
)

interface HyperChatCommandManager {
    fun register(registration: HyperChatCommandRegistration)
    fun unregister(name: String)
    fun executeChat(source: CommandSource, chat: String): Boolean
    fun getRegisteredCommands(): Collection<HyperChatCommandRegistration>
}

interface HyperChatCommandManagerProvider {
    val chatCommandManager: HyperChatCommandManager
}
