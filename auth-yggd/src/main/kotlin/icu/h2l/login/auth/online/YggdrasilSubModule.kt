package icu.h2l.login.auth.online

import icu.h2l.api.HyperZoneApi
import icu.h2l.api.db.HyperZoneDatabaseManager
import icu.h2l.api.db.table.ProfileTable
import icu.h2l.api.module.HyperSubModule
import icu.h2l.login.auth.online.db.EntryTableManager
import icu.h2l.login.auth.online.manager.EntryConfigManager

class YggdrasilSubModule : HyperSubModule {
    lateinit var entryConfigManager: EntryConfigManager
    lateinit var entryTableManager: EntryTableManager
    lateinit var yggdrasilAuthModule: YggdrasilAuthModule

    override fun register(api: HyperZoneApi) {
        val proxy = api.proxy
        val dataDirectory = api.dataDirectory
        val databaseManager: HyperZoneDatabaseManager = api.databaseManager

        val entryConfigManager = EntryConfigManager(dataDirectory, proxy)
        val entryTableManager = EntryTableManager(
            databaseManager = databaseManager,
            tablePrefix = databaseManager.tablePrefix,
            profileTable = ProfileTable(databaseManager.tablePrefix)
        )

        proxy.eventManager.register(api, entryTableManager)

        entryConfigManager.loadAllConfigs()
        entryTableManager.createAllEntryTables()

        val yggdrasilAuthModule = YggdrasilAuthModule(
            proxy = proxy,
            entryConfigManager = entryConfigManager,
            databaseManager = databaseManager,
            entryTableManager = entryTableManager,
            playerAccessor = api.hyperZonePlayers
        )
        val yggdrasilEventListener = YggdrasilEventListener(yggdrasilAuthModule)

        proxy.eventManager.register(api, yggdrasilEventListener)

        this.entryConfigManager = entryConfigManager
        this.entryTableManager = entryTableManager
        this.yggdrasilAuthModule = yggdrasilAuthModule

    }
}
