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

package icu.h2l.login.auth.offline.listener

// HyperZonePlayerManager.create(...) belongs to core pre-login initialization and is intentionally
// kept in the core `velocity` EventListener. Do not initialize channel here to avoid ordering issues.
import com.velocitypowered.api.event.Subscribe
import icu.h2l.api.event.connection.OpenPreLoginEvent
import icu.h2l.api.log.info
import icu.h2l.api.profile.HyperZoneProfileServiceProvider
import icu.h2l.api.profile.PendingUpgradeManager
import icu.h2l.api.profile.ProfileChannelBindingRegistry
import icu.h2l.login.auth.offline.config.AuthOfflineConfigLoader
import icu.h2l.login.auth.offline.type.OfflineUUIDType
import icu.h2l.login.auth.offline.util.ExtraUuidUtils
import net.kyori.adventure.text.Component

class OfflinePreLoginListener {
    @Subscribe
    fun onPreLogin(event: OpenPreLoginEvent) {
        val uuid = event.uuid
        val name = event.userName
        val host = event.host
        // channel/player initialization is performed by the main plugin's EventListener

        val cfg = AuthOfflineConfigLoader.getConfig().match
        if (!cfg.enable) return

        val offlineHost = cfg.hostMatch.start.any { it.startsWith(host) }
        if (offlineHost) {
            info { "匹配到离线 host 玩家: $name" }
        }
        val offlineUUIDType = ExtraUuidUtils.matchType(uuid, name)

        val isOnline = !(offlineUUIDType != OfflineUUIDType.UNKNOWN || offlineHost)
        event.isOnline = isOnline
        info { "传入 UUID 信息玩家: $name UUID:$uuid 类型: $offlineUUIDType 在线:$isOnline" }

        // 若为离线模式玩家，检查其名称是否对应已绑定 Yggdrasil 的 Profile
        // 或是正在等待升级（刚执行完 /upgrade）
        // 若是则直接踢出，禁止以离线方式登录
        if (!isOnline) {
            val profileService = HyperZoneProfileServiceProvider.getOrNull() ?: return
            val profile = profileService.findProfileByName(name) ?: return
            if (PendingUpgradeManager.hasPending(profile.id)) {
                info { "踢出离线玩家 $name: 此账号正在等待升级，必须使用皮肤站/正版登录" }
                event.allow = false
                event.disconnectMessage = Component.text("§e您已申请升级账号，请使用皮肤站/正版启动游戏登录以完成升级")
                return
            }
            if (ProfileChannelBindingRegistry.isProfileBoundToChannel(profile.id, "yggdrasil")) {
                info { "踢出离线玩家 $name: 该账号已绑定皮肤站/正版" }
                event.allow = false
                event.disconnectMessage = Component.text("此账号已绑定皮肤站/正版，请使用皮肤站启动游戏登录")
            }
        }
    }
}


