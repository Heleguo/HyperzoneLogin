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

@ConfigSerializable
class ModulesConfig {
    @Comment("是否启用内置离线认证模块；若同时安装 hzl-auth-offline 外部插件，则自动跳过内置版本")
    val authOffline: Boolean = true

    @Comment("是否启用内置 Yggdrasil 认证模块；若同时安装 hzl-auth-yggd 外部插件，则自动跳过内置版本")
    val authYggd: Boolean = true

    @Comment("是否启用内置安全防护模块；若同时安装 hzl-safe 外部插件，则自动跳过内置版本")
    val safe: Boolean = true

    @Comment("是否启用内置皮肤缓存模块；若同时安装 hzl-profile-skin 外部插件，则自动跳过内置版本")
    val profileSkin: Boolean = true

    @Comment("是否启用内置数据迁移模块；默认关闭，按需开启。若同时安装 hzl-data-merge 外部插件，则自动跳过内置版本")
    val dataMerge: Boolean = false
}

