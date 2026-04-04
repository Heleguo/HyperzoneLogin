package icu.h2l.api.connection

import com.velocitypowered.api.proxy.InboundConnection
import com.velocitypowered.proxy.connection.MinecraftConnection
import com.velocitypowered.proxy.connection.client.InitialInboundConnection
import com.velocitypowered.proxy.connection.client.LoginInboundConnection
import io.netty.channel.Channel

private val initialInboundConnectionDelegateField by lazy {
    LoginInboundConnection::class.java.getDeclaredField("delegate").apply {
        isAccessible = true
    }
}

private val delegateGetConnectionMethod by lazy {
    initialInboundConnectionDelegateField.type.getDeclaredMethod("getConnection").apply {
        isAccessible = true
    }
}

fun LoginInboundConnection.getLoginInbondNettyChannel(): Channel = this.let { loginInboundConnection ->
    val delegate = initialInboundConnectionDelegateField.get(loginInboundConnection)
    val minecraftConnection = delegateGetConnectionMethod.invoke(delegate) as MinecraftConnection
    minecraftConnection.channel
}

fun InboundConnection.getNettyChannel(): Channel {
    if (this is InitialInboundConnection) {
        return this.connection.channel
    } else if (this is LoginInboundConnection) {
        return this.getLoginInbondNettyChannel()
    }
    throw IllegalStateException("未知InboundConnection类型${this.javaClass}")
}