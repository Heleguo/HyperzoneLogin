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
import com.velocitypowered.api.event.player.GameProfileRequestEvent
import icu.h2l.api.connection.getNettyChannel
import icu.h2l.api.event.profile.VerifyInitialGameProfileEvent
import icu.h2l.api.log.HyperZoneDebugType
import icu.h2l.api.log.debug
import icu.h2l.login.HyperZoneLoginMain

class LoginVerifyListener {

    @Subscribe
    fun onGameProfileRequestEvent(event: GameProfileRequestEvent) {
        val incomingProfile = event.gameProfile
        val incomingName = incomingProfile.name
        val verifyEvent = VerifyInitialGameProfileEvent(event.connection, incomingProfile)
        debug(HyperZoneDebugType.OUTPRE_TRACE) {
            "loginVerify.onGameProfileRequest channel=${event.connection.getNettyChannel()} incomingName=${incomingProfile.name} incomingUuid=${incomingProfile.id}"
        }

        try {
            HyperZoneLoginMain.getInstance().proxy.eventManager.fire(verifyEvent).join()
        } catch (t: Throwable) {
            HyperZoneLoginMain.getInstance().logger.error("初始 GameProfile 扩展校验事件执行失败: ${t.message}", t)
        }

        debug(HyperZoneDebugType.OUTPRE_TRACE) {
            "loginVerify.afterVerifyEvent channel=${event.connection.getNettyChannel()} incomingName=${incomingProfile.name} pass=${verifyEvent.pass}"
        }

        // 临时 GameProfile 功能已移除，不再校验 HZL 前缀/UUID
        // 直接允许通过，由各认证模块的监听器自行处理
        return
    }
}

