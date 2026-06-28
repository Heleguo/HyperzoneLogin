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

package icu.h2l.api.profile

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 跨模块的 Profile 渠道绑定检查注册表。
 *
 * 各认证模块（如 auth-yggd）可在初始化时注册检查函数，
 * 其他模块（如 auth-offline）可通过 [isProfileBoundToAnyExternalChannel] 查询
 * 指定 Profile 是否已被其他渠道绑定。
 */
object ProfileChannelBindingRegistry {

    private val checkers = ConcurrentHashMap<String, (UUID) -> Boolean>()

    /**
     * 注册一个渠道绑定检查器。
     *
     * @param channelId 渠道 ID，如 "yggdrasil"
     * @param checker 检查函数，接收 profileId，返回是否已绑定
     */
    fun register(channelId: String, checker: (UUID) -> Boolean) {
        checkers[channelId] = checker
    }

    /**
     * 注销一个渠道绑定检查器。
     */
    fun unregister(channelId: String) {
        checkers.remove(channelId)
    }

    /**
     * 检查指定 Profile 是否已被任何外部渠道绑定。
     */
    fun isProfileBoundToAnyExternalChannel(profileId: UUID): Boolean {
        return checkers.values.any { it(profileId) }
    }
}
