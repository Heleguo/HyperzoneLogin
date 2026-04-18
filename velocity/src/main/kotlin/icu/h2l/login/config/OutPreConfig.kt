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

package icu.h2l.login.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment
import java.net.InetSocketAddress

@Suppress("ANNOTATION_WILL_BE_APPLIED_ALSO_TO_PROPERTY_OR_FIELD")
@ConfigSerializable
data class OutPreConfig(
    @Comment(
        """outpre 认证服的逻辑名，仅用于日志/状态标识；不需要在 Velocity 中注册
如果使用ViaVersion，你需要在Velocity中添加注册条目，如outpre-auth = "127.0.0.1:30066"，但不需要添加到try中"""
    )
    val authLabel: String = "outpre-auth",

    @Comment("outpre 认证服的直连 Host；留空表示禁用 outpre")
    val authHost: String = "127.0.0.1",

    @Comment("outpre 认证服的直连 Port")
    val authPort: Int = 30066,

    @Comment("认证完成后默认进入的子服务器；留空表示不指定固定目标")
    val postAuthDefaultServer: String = "play",

    @Comment("在 outpre 认证阶段，如果玩家尝试前往其他服务器，是否记住该目标并在认证成功后优先前往")
    val rememberRequestedServerDuringAuth: Boolean = true,

    @Comment("转接给认证服时，在握手中对后端暴露的 Host；留空时使用 authServer 的注册地址")
    val presentedHost: String = "",

    @Comment("转接给认证服时，在握手中对后端暴露的 Port；<=0 时使用 authServer 的注册端口")
    val presentedPort: Int = -1,

    @Comment("转接给认证服时，对后端暴露的玩家 IP；留空时使用客户端真实 IP")
    val presentedPlayerIp: String = ""
) {
    fun resolveAuthAddress(): InetSocketAddress? {
        val host = authHost.trim()
        if (host.isBlank()) {
            return null
        }
        if (authPort !in 1..65535) {
            return null
        }
        return InetSocketAddress.createUnresolved(host, authPort)
    }

    fun authTargetLabel(): String {
        return authLabel.trim().ifBlank { "${authHost.trim()}:$authPort" }
    }

    fun resolvePresentedHost(authAddress: InetSocketAddress): String {
        return presentedHost.trim().ifBlank { authAddress.hostString }
    }

    fun resolvePresentedPort(authAddress: InetSocketAddress): Int {
        return presentedPort.takeIf { it in 1..65535 } ?: authAddress.port
    }

    fun resolvePresentedPlayerIp(clientIp: String): String {
        return presentedPlayerIp.trim().ifBlank { clientIp }
    }
}
