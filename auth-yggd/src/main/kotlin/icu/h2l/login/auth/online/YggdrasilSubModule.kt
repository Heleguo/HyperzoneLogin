package icu.h2l.login.auth.online

import com.velocitypowered.api.proxy.ProxyServer
import `fun`.iiii.h2l.api.db.HyperZoneDatabaseManager
import `fun`.iiii.h2l.api.db.table.ProfileTable
import `fun`.iiii.h2l.api.module.HyperSubModule
import icu.h2l.login.auth.online.db.EntryTableManager
import icu.h2l.login.auth.online.manager.EntryConfigManager
import java.util.logging.Logger
import java.nio.file.Path

class YggdrasilSubModule : HyperSubModule {
    lateinit var entryConfigManager: EntryConfigManager
    lateinit var entryTableManager: EntryTableManager
    lateinit var yggdrasilAuthModule: YggdrasilAuthModule

    override fun register(
        owner: Any,
        proxy: ProxyServer,
        dataDirectory: Path,
        databaseManager: HyperZoneDatabaseManager
    ) {
        val entryConfigManager = EntryConfigManager(dataDirectory, proxy)
        val entryTableManager = EntryTableManager(
            logger = Logger.getLogger("HyperZoneLogin-AuthYggd"),
            databaseManager = databaseManager,
            tablePrefix = databaseManager.tablePrefix,
            profileTable = ProfileTable(databaseManager.tablePrefix)
        )

        proxy.eventManager.register(owner, entryTableManager)

        entryConfigManager.loadAllConfigs()

        val yggdrasilAuthModule = YggdrasilAuthModule(
            entryConfigManager = entryConfigManager,
            databaseManager = databaseManager,
            entryTableManager = entryTableManager
        )

        this.entryConfigManager = entryConfigManager
        this.entryTableManager = entryTableManager
        this.yggdrasilAuthModule = yggdrasilAuthModule

    }
}
