package icu.h2l.login.inject.network.listener

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ListenerBoundEvent
import com.velocitypowered.api.network.ListenerType
import icu.h2l.login.inject.network.VelocityNetworkInjectorImpl

class NetworkInjectListener(
    private val injector: VelocityNetworkInjectorImpl,
) {

    @Subscribe
    fun onAddressBound(event: ListenerBoundEvent) {
        if (event.listenerType != ListenerType.MINECRAFT) return

        injector.injectToServerPipeline()
    }
}