package icu.h2l.login.auth.offline

import icu.h2l.api.HyperZoneApi
import icu.h2l.api.db.HyperZoneDatabaseManager
import icu.h2l.api.db.table.ProfileTable
import icu.h2l.api.log.info
import icu.h2l.api.module.HyperSubModule
import icu.h2l.login.auth.offline.command.OfflineAuthCommandRegistrar
import icu.h2l.login.auth.offline.db.OfflineAuthRepository
import icu.h2l.login.auth.offline.db.OfflineAuthTableManager
import icu.h2l.login.auth.offline.service.OfflineAuthService
import icu.h2l.login.auth.offline.config.OfflineMatchConfigLoader
import icu.h2l.login.auth.offline.listener.OfflinePreLoginListener
class OfflineSubModule : HyperSubModule {
    lateinit var offlineAuthTableManager: OfflineAuthTableManager
    lateinit var offlineAuthRepository: OfflineAuthRepository
    lateinit var offlineAuthService: OfflineAuthService

    override fun register(api: HyperZoneApi) {
        val proxy = api.proxy
        val dataDirectory = api.dataDirectory
        val databaseManager: HyperZoneDatabaseManager = api.databaseManager

        val profileTable = ProfileTable(databaseManager.tablePrefix)
        // Load offline matching configuration for this module
        OfflineMatchConfigLoader.load(dataDirectory)
        offlineAuthTableManager = OfflineAuthTableManager(
            databaseManager = databaseManager,
            tablePrefix = databaseManager.tablePrefix,
            profileTable = profileTable
        )
        offlineAuthRepository = OfflineAuthRepository(
            databaseManager = databaseManager,
            table = offlineAuthTableManager.offlineAuthTable
        )
        offlineAuthService = OfflineAuthService(
            repository = offlineAuthRepository,
            playerAccessor = api.hyperZonePlayers
        )
        offlineAuthTableManager.createTable()
        proxy.eventManager.register(api, offlineAuthTableManager)

        // Register pre-login listener (handles channel init + offline UUID matching)
        proxy.eventManager.register(api, OfflinePreLoginListener())

        OfflineAuthCommandRegistrar.registerAll(
            commandManager = api.chatCommandManager,
            authService = offlineAuthService
        )
        proxy.eventManager.register(api, OfflineLimboEventListener())
        info { "OfflineSubModule 已加载，离线聊天命令与提示监听器已注册" }
    }
}