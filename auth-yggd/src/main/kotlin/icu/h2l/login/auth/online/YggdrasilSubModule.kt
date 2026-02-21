package icu.h2l.login.auth.online

import com.velocitypowered.api.proxy.ProxyServer
import icu.h2l.api.db.HyperZoneDatabaseManager
import icu.h2l.api.db.table.ProfileTable
import icu.h2l.api.module.HyperSubModule
import icu.h2l.api.player.HyperZonePlayerAccessorProvider
import icu.h2l.login.auth.online.db.EntryTableManager
import icu.h2l.login.auth.online.manager.EntryConfigManager
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
        val playerAccessorProvider = owner as? HyperZonePlayerAccessorProvider
            ?: throw IllegalStateException("YggdrasilSubModule requires HyperZonePlayerAccessorProvider owner")

        val entryConfigManager = EntryConfigManager(dataDirectory, proxy)
        val entryTableManager = EntryTableManager(
            databaseManager = databaseManager,
            tablePrefix = databaseManager.tablePrefix,
            profileTable = ProfileTable(databaseManager.tablePrefix)
        )

        proxy.eventManager.register(owner, entryTableManager)

        entryConfigManager.loadAllConfigs()
        entryTableManager.createAllEntryTables()

        val yggdrasilAuthModule = YggdrasilAuthModule(
            entryConfigManager = entryConfigManager,
            databaseManager = databaseManager,
            entryTableManager = entryTableManager,
            playerAccessor = playerAccessorProvider.hyperZonePlayers
        )
        val yggdrasilEventListener = YggdrasilEventListener(yggdrasilAuthModule)

        proxy.eventManager.register(owner, yggdrasilEventListener)

        this.entryConfigManager = entryConfigManager
        this.entryTableManager = entryTableManager
        this.yggdrasilAuthModule = yggdrasilAuthModule

    }
}
