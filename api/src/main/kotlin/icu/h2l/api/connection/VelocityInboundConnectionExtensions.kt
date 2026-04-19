/*
 * This file is part of HyperZoneLogin, licensed under the GNU Affero General Public License v3.0 or later.
 *
 * Copyright (C) ksqeib (庆灵) <ksqeib@qq.com>
 * Copyright (C) contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package icu.h2l.api.connection

import com.velocitypowered.api.proxy.InboundConnection
import com.velocitypowered.proxy.connection.MinecraftConnection
import com.velocitypowered.proxy.connection.client.InitialInboundConnection
import com.velocitypowered.proxy.connection.client.LoginInboundConnection
import io.netty.channel.Channel
import net.kyori.adventure.text.Component

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

/**
 * 向当前入站连接发送断开消息。
 *
 * 该扩展会兼容 Velocity 在不同登录阶段使用的不同连接实现。
 */
fun InboundConnection.disconnectWithMessage(userMessage: Component) {

    if (this is InitialInboundConnection) {
        this.disconnect(userMessage)
        return
    } else if (this is LoginInboundConnection) {
        this.getDelegate().disconnect(userMessage)
        return
    }
    throw IllegalStateException("未知InboundConnection类型${this.javaClass}")
}

/**
 * 读取 [LoginInboundConnection] 内部持有的 [InitialInboundConnection] 委托对象。
 */
fun LoginInboundConnection.getDelegate(): InitialInboundConnection = this.let { loginInboundConnection ->
    val delegate = initialInboundConnectionDelegateField.get(loginInboundConnection) as InitialInboundConnection
    delegate
}

/**
 * 获取登录阶段连接所对应的 Netty [Channel]。
 */
fun LoginInboundConnection.getLoginInbondNettyChannel(): Channel = this.let { loginInboundConnection ->
    val delegate = initialInboundConnectionDelegateField.get(loginInboundConnection)
    val minecraftConnection = delegateGetConnectionMethod.invoke(delegate) as MinecraftConnection
    minecraftConnection.channel
}

/**
 * 获取当前入站连接底层使用的 Netty [Channel]。
 */
fun InboundConnection.getNettyChannel(): Channel {
    if (this is InitialInboundConnection) {
        return this.connection.channel
    } else if (this is LoginInboundConnection) {
        return this.getLoginInbondNettyChannel()
    }
    throw IllegalStateException("未知InboundConnection类型${this.javaClass}")
}