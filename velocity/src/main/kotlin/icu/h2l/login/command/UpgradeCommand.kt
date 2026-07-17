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

        val authChannelId = hyperZonePlayer.authChannelId
        if (authChannelId == null) {
            messages.send(source, MessageKeys.Upgrade.NOT_AUTHENTICATED)
            return
        }

        // 前提 2: 查询 auth_mode 表，auth_type 必须为 OFFLINE
        val profileId = main.profileService.getAttachedProfile(hyperZonePlayer)?.id
        if (profileId == null) {
            messages.send(source, MessageKeys.Upgrade.FAILED)
            return
        }

        val entry = main.authModeRepository.getByUuid(profileId)
        if (entry == null || entry.authType != "OFFLINE") {
            messages.send(source, MessageKeys.Upgrade.NOT_OFFLINE_ACCOUNT)
            return
        }

        // 前提 3: 当前连接方式为在线（凭证渠道必须是 yggdrasil 或其他在线渠道）
        if (authChannelId != "yggdrasil") {
            messages.send(source, MessageKeys.Upgrade.NOT_ONLINE_CONNECTION)
            return
        }

        // 确定目标 auth_type：根据当前凭证渠道
        val targetAuthType = when (authChannelId) {
            "yggdrasil" -> "YGGDRASIL"
            else -> {
                messages.send(source, MessageKeys.Upgrade.NOT_ONLINE_CONNECTION)
                return
            }
        }

        // 执行升级：更新 auth_mode 表
        val updated = main.authModeRepository.updateAuthType(profileId, targetAuthType)
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
