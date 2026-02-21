package icu.h2l.login.inject.network

import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.proxy.VelocityServer
import com.velocitypowered.proxy.network.ConnectionManager
import icu.h2l.api.db.HyperZoneDatabaseManager
import icu.h2l.api.module.HyperSubModule
import icu.h2l.login.inject.network.listener.NetworkInjectListener
import java.nio.file.Path

class VelocityNetworkModule : HyperSubModule {
    override fun register(
        owner: Any,
        proxy: ProxyServer,
        dataDirectory: Path,
        databaseManager: HyperZoneDatabaseManager,
    ) {
        proxy as VelocityServer

        val connectionManager = VelocityServer::class.java.getDeclaredField("cm").also {
            it.isAccessible = true
        }.get(proxy) as ConnectionManager

        val injector = VelocityNetworkInjectorImpl(connectionManager, proxy)

        proxy.eventManager.register(
            owner,
            NetworkInjectListener(injector),
        )

        injector.injectToBackend()
    }
}