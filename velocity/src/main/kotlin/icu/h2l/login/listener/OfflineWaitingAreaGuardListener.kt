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

import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.ServerPreConnectEvent
import icu.h2l.login.HyperZoneLoginMain
import icu.h2l.login.manager.HyperZonePlayerManager
import icu.h2l.login.message.MessageKeys

/**
 * 离线未认证玩家的等待区守卫监听器。
 *
 * 高优先级拦截 [ServerPreConnectEvent]，对离线模式且未完成验证的玩家拒绝所有服务器切换，
 * 防止其通过 /server 命令或其他方式跳出等待区。
 *
 * 此监听器作为独立的安全层工作，不依赖 BackendHoldState 或 OutPreState，
 * 即使后端状态因竞态条件丢失仍能生效。
 */
class OfflineWaitingAreaGuardListener {

    @Subscribe(order = PostOrder.EARLY)
    fun onServerPreConnect(event: ServerPreConnectEvent) {
        val player = event.player
        val hyperPlayer = HyperZonePlayerManager.getByPlayerOrNull(player) ?: return
        if (hyperPlayer.isOnlinePlayer) {
            return
        }
        if (hyperPlayer.isVerified() && hyperPlayer.hasAttachedProfile()) {
            return
        }

        // 离线未认证玩家，拒绝所有服务器切换
        event.result = ServerPreConnectEvent.ServerResult.denied()
        val messages = HyperZoneLoginMain.getInstance().messageService
        messages.send(player, MessageKeys.Chat.OFFLINE_CANNOT_LEAVE_WAITING_AREA)
    }
}
