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

package icu.h2l.login.listener

import com.velocitypowered.api.event.Subscribe
import icu.h2l.api.event.profile.ProfileAttachedEvent
import icu.h2l.login.HyperZoneLoginMain

/**
 * 监听 ProfileAttachedEvent，在玩家完成认证并 attach Profile 后，
 * 根据其认证渠道向 auth_mode 表写入或更新认证方式记录。
 */
class AuthModeWriteListener {

    @Subscribe
    fun onProfileAttached(event: ProfileAttachedEvent) {
        val main = HyperZoneLoginMain.getInstance()
        if (!::main.authModeRepository.isInitialized) return

        val hyperPlayer = event.hyperZonePlayer
        val profile = event.profile
        val authChannelId = hyperPlayer.authChannelId ?: return

        // 根据认证渠道确定 auth_type
        val authType = when (authChannelId) {
            "yggdrasil" -> "YGGDRASIL"
            "offline" -> "OFFLINE"
            "floodgate" -> "FLOODGATE"
            else -> return // 未知渠道，不写入
        }

        val playerUuid = profile.uuid
        val playerName = profile.name

        // 检查是否已存在记录
        val existingEntry = main.authModeRepository.getByUuid(playerUuid)
        if (existingEntry != null) {
            // 更新已有记录
            main.authModeRepository.updatePlayerName(playerUuid, playerName)
            if (existingEntry.authType == "OFFLINE" && authType != "OFFLINE") {
                main.authModeRepository.updateAuthType(playerUuid, authType)
            }
        } else {
            // 创建新记录
            main.authModeRepository.create(playerUuid, playerName, authType)
        }
    }
}
