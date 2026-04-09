package icu.h2l.api

import com.velocitypowered.api.proxy.ProxyServer
import icu.h2l.api.command.HyperChatCommandManagerProvider
import icu.h2l.api.db.HyperZoneDatabaseManager
import icu.h2l.api.module.HyperSubModule
import icu.h2l.api.player.HyperZonePlayerAccessorProvider
import icu.h2l.api.vServer.HyperZoneVServerProvider
import java.nio.file.Path

interface HyperZoneApi :
    HyperChatCommandManagerProvider,
    HyperZonePlayerAccessorProvider,
    HyperZoneVServerProvider {
    val proxy: ProxyServer
    val dataDirectory: Path
    val databaseManager: HyperZoneDatabaseManager

    fun registerModule(module: HyperSubModule)
}

object HyperZoneApiProvider {
    @Volatile
    private var api: HyperZoneApi? = null

    fun bind(api: HyperZoneApi) {
        this.api = api
    }

    fun get(): HyperZoneApi = api ?: error("HyperZoneLogin API is not available yet")

    fun getOrNull(): HyperZoneApi? = api
}

