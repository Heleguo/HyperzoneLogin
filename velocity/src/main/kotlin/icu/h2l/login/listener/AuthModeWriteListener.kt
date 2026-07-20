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
        val authModeRepo = main.authModeRepository

        val hyperPlayer = event.hyperZonePlayer
        val profile = event.profile
        val authChannelId = hyperPlayer.authChannelId ?: return

        // 获取 Yggdrasil Entry ID（由认证模块在 submitCredential 前通过 setAuthEntryId 记录）
        val authEntryId = hyperPlayer.getAuthEntryId()

        // 根据认证渠道和 Entry ID 确定 auth_type
        // "mojang" Entry → MOJANG，其他 Yggdrasil Entry → YGGDRASIL
        val authType = when (authChannelId) {
            "yggdrasil" -> if (authEntryId == "mojang") "MOJANG" else "YGGDRASIL"
            "offline" -> "OFFLINE"
            "floodgate" -> "FLOODGATE"
            else -> return // 未知渠道，不写入
        }

        val playerUuid = profile.uuid
        val playerName = profile.name

        // 检查是否已存在记录（以 player_name 为依据查询，防止同名不同 UUID 的冲突被忽略）
        val existingEntry = authModeRepo.getByName(playerName)
        if (existingEntry != null) {
            // 使用已有记录的 UUID 进行更新（防止不同 UUID 的同名玩家覆盖已有记录）
            val existingUuid = existingEntry.playerUuid
            authModeRepo.updatePlayerName(existingUuid, playerName)
            if (existingEntry.authType == "OFFLINE" && authType != "OFFLINE") {
                authModeRepo.updateAuthType(existingUuid, authType)
            }
            // 仅在 auth_entry_id 为 null 时补录（首次连接/旧记录迁移）
            // 禁止覆盖已有 entry——防止非同源 Entry 覆盖已绑定来源
            if (authEntryId != null && existingEntry.authEntryId == null) {
                authModeRepo.updateAuthEntryId(existingUuid, authEntryId)
            }
        } else {
            // 创建新记录
            authModeRepo.create(playerUuid, playerName, authType, authEntryId)
        }
    }
}
