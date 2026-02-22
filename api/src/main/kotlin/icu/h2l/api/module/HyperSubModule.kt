package icu.h2l.api.module

import com.velocitypowered.api.proxy.ProxyServer
import icu.h2l.api.db.HyperZoneDatabaseManager
import java.nio.file.Path

interface HyperSubModule {
    fun register(owner: Any, proxy: ProxyServer, dataDirectory: Path, databaseManager: HyperZoneDatabaseManager)
}
