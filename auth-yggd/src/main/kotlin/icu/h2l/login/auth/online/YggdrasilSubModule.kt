package icu.h2l.login.auth.online

import com.velocitypowered.api.proxy.ProxyServer
import `fun`.iiii.h2l.api.module.HyperSubModule
import icu.h2l.login.auth.online.manager.EntryConfigManager
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import java.nio.file.Path

class YggdrasilSubModule : HyperSubModule {
    lateinit var entryConfigManager: EntryConfigManager
    lateinit var yggdrasilAuthModule: YggdrasilAuthModule
    override fun register(main: Any) {
        val mainClass = main.javaClass

        val proxy = mainClass.getMethod("getProxy").invoke(main) as ProxyServer
        val logger = mainClass.getMethod("getLogger").invoke(main) as ComponentLogger
        val dataDirectory = mainClass.getMethod("getDataDirectoryPath").invoke(main) as Path
        val databaseManager = mainClass.getMethod("getDatabaseManager").invoke(main) as DatabaseManager

        val entryConfigManager = EntryConfigManager(dataDirectory, logger, proxy)

        val entryTableManager = databaseManager.javaClass.getMethod("getEntryTableManager").invoke(databaseManager)
        proxy.eventManager.register(main, entryTableManager)

        entryConfigManager.loadAllConfigs()

        val yggdrasilAuthModule = YggdrasilAuthModule(entryConfigManager, databaseManager)

        mainClass.getMethod("registerEntryConfigManager", EntryConfigManager::class.java)
            .invoke(main, entryConfigManager)
        mainClass.getMethod("registerYggdrasilAuthModule", YggdrasilAuthModule::class.java)
            .invoke(main, yggdrasilAuthModule)
    }
}
