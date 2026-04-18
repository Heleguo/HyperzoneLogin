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

package icu.h2l.login.vServer.outpre.listener

import com.velocitypowered.api.event.Subscribe
import icu.h2l.api.connection.getNettyChannel
import icu.h2l.api.event.profile.VerifyInitialGameProfileEvent
import icu.h2l.login.HyperZoneLoginMain
import icu.h2l.login.vServer.outpre.OutPreVServerAuth

/**
 * outpre 模式在真正交付给 Velocity 之前，会自行桥接并维护初始登录态。
 *
 * 因此这里需要显式豁免核心的 remap 前缀/UUID 校验，
 * 避免 `LoginVerifyListener` 把 outpre 的原始客户端档案误判为插件冲突。
 */
class OutPreVerifyInitialGameProfileListener {
    @Subscribe
    fun onVerifyInitialGameProfileEvent(event: VerifyInitialGameProfileEvent) {
        val outPreAdapter = HyperZoneLoginMain.getInstance().serverAdapter as? OutPreVServerAuth
            ?: return
        val channel = event.connection.getNettyChannel()
        if (!outPreAdapter.shouldPassInitialVerifyProfile(channel, event.gameProfile)) {
            return
        }

        event.pass = true
    }
}


