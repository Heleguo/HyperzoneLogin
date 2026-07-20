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

package icu.h2l.login.vServer.outpre.session

import com.velocitypowered.proxy.connection.client.ConnectedPlayer
import icu.h2l.api.event.auth.AuthenticationFailureEvent
import icu.h2l.api.player.getChannel
import icu.h2l.login.player.VelocityHyperZonePlayer
import icu.h2l.login.vServer.outpre.OutPreState
import icu.h2l.login.vServer.outpre.OutPreVServerAuth
import icu.h2l.login.vServer.outpre.handler.OutPreAuthSessionHandler

/**
 * struck 模式的初始进入会话。
 *
 * 该模式默认不接入认证服桥接；若线上认证失败/超时，再切换成 waiting-area bridge 会话。
 */
internal class StruckInitialJoinSession(
    override val player: ConnectedPlayer,
    override val hyperPlayer: VelocityHyperZonePlayer,
    override val handler: OutPreAuthSessionHandler,
    override val state: OutPreState,
) : OutPreInitialJoinSession {
    override fun begin(owner: OutPreVServerAuth) {
        owner.publishAuthStartEvent(player, hyperPlayer, state, "beginInitialJoin")
        owner.trace(
            "beginInitialJoin struck-suspended channel=${player.getChannel().id()} player=${player.username} ${
                owner.describeState(state)
            }"
        )
    }

    override fun onVerified(owner: OutPreVServerAuth) {
        owner.continueVerifiedInitialJoin(player, handler, state)
    }

    override fun onAuthServerJoined(owner: OutPreVServerAuth) = Unit

    override fun onAuthenticationFailure(owner: OutPreVServerAuth, event: AuthenticationFailureEvent): Boolean {
        owner.switchStruckSessionToBridgeWaitingArea(this, event)
        return true
    }
}
