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

package icu.h2l.login.util

import com.velocitypowered.proxy.config.VelocityConfiguration
import java.lang.reflect.Method

/**
 * 兼容上游 Velocity 和 Velocity-CTD 的 PlayerInfoForwarding 检测工具。
 *
 * 上游 Velocity 中 [PlayerInfoForwarding] 在 `com.velocitypowered.proxy.config` 包，
 * Velocity-CTD 将其移到了 `com.velocitypowered.api.proxy.server` 包。
 * 通过反射获取枚举常量和调用 getter 以避免编译期导入冲突。
 */
object PlayerInfoForwardingCompat {

    private val getterMethod: Method? by lazy {
        runCatching { VelocityConfiguration::class.java.getMethod("getPlayerInfoForwardingMode") }.getOrNull()
    }

    private val modernEnumConstant: Any? by lazy { resolveEnumConstant("MODERN") }
    private val noneEnumConstant: Any? by lazy { resolveEnumConstant("NONE") }

    private fun resolveEnumConstant(name: String): Any? {
        val fromUpstream = runCatching {
            Class.forName("com.velocitypowered.proxy.config.PlayerInfoForwarding")
                .getDeclaredField(name)
                .get(null)
        }.getOrNull()
        if (fromUpstream != null) return fromUpstream
        return runCatching {
            Class.forName("com.velocitypowered.api.proxy.server.PlayerInfoForwarding")
                .getDeclaredField(name)
                .get(null)
        }.getOrNull()
    }

    private fun getCurrentMode(config: VelocityConfiguration): Any? {
        return getterMethod?.invoke(config)
    }

    fun isModernForwardingMode(config: VelocityConfiguration): Boolean {
        val current = getCurrentMode(config) ?: return false
        return current == modernEnumConstant
    }

    fun isNoneForwardingMode(config: VelocityConfiguration): Boolean {
        val current = getCurrentMode(config) ?: return false
        return current == noneEnumConstant
    }

    fun getForwardingModeValue(config: VelocityConfiguration): Any? {
        return getCurrentMode(config)
    }
}
