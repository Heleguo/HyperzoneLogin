package icu.h2l.login.profile.skin

import com.velocitypowered.api.proxy.ProxyServer
import icu.h2l.api.db.HyperZoneDatabaseManager
import icu.h2l.api.db.table.ProfileTable
import icu.h2l.api.log.info
import icu.h2l.api.module.HyperSubModule
import icu.h2l.login.profile.skin.config.ProfileSkinConfigLoader
import icu.h2l.login.profile.skin.db.ProfileSkinCacheRepository
import icu.h2l.login.profile.skin.db.ProfileSkinCacheTable
import icu.h2l.login.profile.skin.db.ProfileSkinCacheTableManager
import icu.h2l.login.profile.skin.service.ProfileSkinService
import java.nio.file.Path

class ProfileSkinSubModule : HyperSubModule {
    lateinit var tableManager: ProfileSkinCacheTableManager
    lateinit var repository: ProfileSkinCacheRepository
    lateinit var service: ProfileSkinService

    override fun register(
        owner: Any,
        proxy: ProxyServer,
        dataDirectory: Path,
        databaseManager: HyperZoneDatabaseManager
    ) {
        val config = ProfileSkinConfigLoader.load(dataDirectory)
        val table = ProfileSkinCacheTable(
            prefix = databaseManager.tablePrefix,
            profileTable = ProfileTable(databaseManager.tablePrefix)
        )

        tableManager = ProfileSkinCacheTableManager(databaseManager, table)
        repository = ProfileSkinCacheRepository(databaseManager, table)
        service = ProfileSkinService(config, repository)

        tableManager.createTable()
        proxy.eventManager.register(owner, tableManager)
        proxy.eventManager.register(owner, service)

        info { "ProfileSkinSubModule 已加载，皮肤缓存与修复监听器已注册" }
    }
}

