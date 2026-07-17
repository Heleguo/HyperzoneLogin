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

package icu.h2l.login.command

import com.velocitypowered.api.proxy.Player
import icu.h2l.api.command.HyperChatBrigadierRegistration
import icu.h2l.api.command.HyperChatCommandExecutor
import icu.h2l.api.command.HyperChatCommandInvocation
import icu.h2l.login.HyperZoneLoginMain
import icu.h2l.login.manager.HyperZonePlayerManager
import icu.h2l.login.message.MessageKeys

/**
 * /upgrade 命令——将离线账号升级为在线认证。
 *
 * 执行前提：
 * 1. 当前会话已认证（isVerified() == true）
 * 2. auth_mode 表中 auth_type == 'OFFLINE'
 * 3. 当前连接方式为在线（凭证渠道为 yggdrasil 等在线渠道）
 *
 * 满足条件后更新 auth_mode 表的 auth_type 字段为 MOJANG 或 YGGDRASIL。
 */
class UpgradeCommand : HyperChatCommandExecutor {
    override fun execute(invocation: HyperChatCommandInvocation) {
        val source = invocation.source()
        val main = HyperZoneLoginMain.getInstance()
        val messages = main.messageService
        if (source !is Player) {
            messages.send(source, MessageKeys.Common.ONLY_PLAYER)
            return
        }

        val hyperZonePlayer = runCatching {
            HyperZonePlayerManager.getByPlayer(source)
        }.getOrElse {
            messages.send(source, MessageKeys.Common.PLAYER_STATE_UNAVAILABLE)
            return
        }

        // 前提 1: 当前会话已认证
        if (!hyperZonePlayer.isVerified()) {
            messages.send(source, MessageKeys.Upgrade.NOT_AUTHENTICATED)
            return
        }

        // 前提 2: 查询 auth_mode 表，auth_type 必须为 OFFLINE
        val attachedProfile = main.profileService.getAttachedProfile(hyperZonePlayer)
        if (attachedProfile == null) {
            messages.send(source, MessageKeys.Upgrade.FAILED)
            return
        }

        // auth_mode 表使用 profile.uuid（游戏 UUID）作为 player_uuid，不是 profile.id（主键）
        val playerUuid = attachedProfile.uuid
        val entry = main.authModeRepository.getByUuid(playerUuid)
        if (entry == null || entry.authType != "OFFLINE") {
            messages.send(source, MessageKeys.Upgrade.NOT_OFFLINE_ACCOUNT)
            return
        }

        // 前提 3: 当前连接方式必须为在线（使用 isOnlinePlayer 判断原始连接方式，
        //         而不是 authChannelId——因为用户可能通过在线方式连接后使用 /login 登录，
        //         此时 authChannelId 被离线凭证覆盖为 "offline"）
        if (!hyperZonePlayer.isOnlinePlayer) {
            messages.send(source, MessageKeys.Upgrade.NOT_ONLINE_CONNECTION)
            return
        }

        // 确定目标 auth_type：根据玩家的在线认证来源判断
        // 通过 OpenStartAuthEvent 的 isOnline 判断——目前仅支持 YGGDRASIL
        val targetAuthType = "YGGDRASIL"

        // 执行升级：更新 auth_mode 表
        val updated = main.authModeRepository.updateAuthType(playerUuid, targetAuthType)
        if (!updated) {
            messages.send(source, MessageKeys.Upgrade.FAILED)
            return
        }

        // 发送成功提示
        val successKey = when (targetAuthType) {
            "MOJANG" -> MessageKeys.Upgrade.SUCCESS_MOJANG
            "YGGDRASIL" -> MessageKeys.Upgrade.SUCCESS_YGGDRASIL
            else -> MessageKeys.Upgrade.SUCCESS_MOJANG
        }
        messages.send(source, successKey)
    }

    companion object {
        fun brigadier(): HyperChatBrigadierRegistration {
            return HyperChatBrigadierRegistration { context ->
                context.literal()
                    .executes { commandContext ->
                        context.execute(commandContext.source)
                    }
            }
        }
    }
}
