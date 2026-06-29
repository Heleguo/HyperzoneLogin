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
import icu.h2l.api.command.HyperChatCommandExecutor
import icu.h2l.api.command.HyperChatCommandInvocation
import icu.h2l.api.profile.PendingUpgradeManager
import icu.h2l.api.profile.ProfileChannelBindingRegistry
import icu.h2l.login.HyperZoneLoginMain
import icu.h2l.login.manager.HyperZonePlayerManager
import icu.h2l.login.message.MessageKeys
import net.kyori.adventure.text.Component

/**
 * 离线→正版升级命令。
 *
 * 离线玩家登录后执行 /upgrade，系统记录升级意向。
 * 玩家随后以正版/皮肤站模式重新连接，系统自动完成绑定。
 */
class UpgradeCommand : HyperChatCommandExecutor {

    override fun execute(invocation: HyperChatCommandInvocation) {
        val source = invocation.source()
        val messages = HyperZoneLoginMain.getInstance().messageService
        if (source !is Player) {
            messages.send(source, MessageKeys.Common.ONLY_PLAYER)
            return
        }

        val hyperPlayer = runCatching {
            HyperZonePlayerManager.getByPlayer(source)
        }.getOrElse {
            messages.send(source, MessageKeys.Common.PLAYER_STATE_UNAVAILABLE)
            return
        }

        // 必须在等待区外（已验证+已attach Profile）
        if (hyperPlayer.isInWaitingArea()) {
            source.sendMessage(Component.text("§c请先完成离线登录后再执行升级"))
            return
        }

        val profileService = HyperZoneLoginMain.getInstance().profileService
        val profileId = profileService.getAttachedProfileId(hyperPlayer)
        if (profileId == null) {
            source.sendMessage(Component.text("§c未找到已绑定的档案，无法升级"))
            return
        }

        // 检查是否为离线绑定
        if (!ProfileChannelBindingRegistry.isProfileBoundToChannel(profileId, "offline")) {
            source.sendMessage(Component.text("§c此账号不是离线账号，无需升级"))
            return
        }

        val args = invocation.arguments()
        val isConfirmed = args.isNotEmpty() && args[0].equals("confirm", ignoreCase = true)

        if (!isConfirmed) {
            source.sendMessage(Component.text("§c⚠ 升级后将无法回退为离线模式，此后只能使用皮肤站/正版登录！"))
            source.sendMessage(Component.text("§c⚠ 请务必使用皮肤站/正版启动游戏重新连接，不要使用离线模式！"))
            source.sendMessage(Component.text("§e如果确认要升级，请使用 §6/upgrade confirm §e继续"))
            return
        }

        // 记录升级待办（后续由 YggdrasilAuthModule 消费）
        val attachedProfile = profileService.getAttachedProfile(hyperPlayer)
        PendingUpgradeManager.addPending(
            offlineProfileId = profileId,
            name = hyperPlayer.clientOriginalName,
            uuid = attachedProfile?.uuid ?: hyperPlayer.clientOriginalUUID
        )

        source.sendMessage(Component.text("§a升级准备已就绪！"))
        source.sendMessage(Component.text("§e请断开当前连接，使用皮肤站/正版启动游戏重新进入服务器。"))
        source.sendMessage(Component.text("§e系统将自动完成升级，之后您将只能使用皮肤站/正版登录（不可回退为离线）。"))
    }
}
