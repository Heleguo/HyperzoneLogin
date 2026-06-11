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

@Suppress("ANNOTATION_WILL_BE_APPLIED_ALSO_TO_PROPERTY_OR_FIELD")
@ConfigSerializable
data class VServerConfig(
    // 登录服实现模式：仅支持 backend 模式
    @Comment("config.vserver.mode")
    val mode: String = "backend",

    // 认证完成后默认进入的服务器
    @Comment("config.vserver.post-auth-default-server")
    val postAuthDefaultServer: String = "play",

    // 记住认证时收到的服务器跳转请求
    @Comment("config.vserver.remember-requested-server")
    val rememberRequestedServerDuringAuth: Boolean = true,

    val backend: BackendConfig = BackendConfig()
) {
    @Suppress("ANNOTATION_WILL_BE_APPLIED_ALSO_TO_PROPERTY_OR_FIELD")
    @ConfigSerializable
    data class BackendConfig(
        // 使用的真实认证等待服 Velocity 服务器名
        @Comment("config.vserver.backend.fallback-auth-server")
        val fallbackAuthServer: String = "lobby",

        // 等待区 UpsertPlayerInfo/TabList 兼容过滤补偿
        @Comment("config.vserver.backend.player-info-compensation")
        val enablePlayerInfoCompensation: Boolean = true,

        // 档案补偿同步
        @Comment("config.vserver.backend.profile-compensation")
        val enableProfileCompensation: Boolean = true,

        // 在线热改 name（风险较低，默认开启）
        @Comment("config.vserver.backend.name-hot-change")
        val enableNameHotChange: Boolean = true,

        // 在线热改 UUID（高风险，默认关闭）
        @Comment("config.vserver.backend.uuid-hot-change")
        val enableUuidHotChange: Boolean = false
    )
}
