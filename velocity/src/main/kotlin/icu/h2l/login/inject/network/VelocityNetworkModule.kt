package icu.h2l.login.inject.network

import com.velocitypowered.proxy.VelocityServer
import com.velocitypowered.proxy.network.ConnectionManager
import icu.h2l.api.HyperZoneApi
import icu.h2l.api.module.HyperSubModule
import icu.h2l.login.inject.network.listener.NetworkInjectListener

class VelocityNetworkModule : HyperSubModule {
    override fun register(api: HyperZoneApi) {
        val proxy = api.proxy as VelocityServer

        val connectionManager = VelocityServer::class.java.getDeclaredField("cm").also {
            it.isAccessible = true
        }.get(proxy) as ConnectionManager

        val injector = VelocityNetworkInjectorImpl(connectionManager, proxy)

        proxy.eventManager.register(
            api,
            NetworkInjectListener(injector),
        )

        injector.injectToBackend()
    }
}