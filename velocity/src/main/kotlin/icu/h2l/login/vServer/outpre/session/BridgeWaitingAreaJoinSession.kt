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
import icu.h2l.api.player.getChannel
import icu.h2l.login.player.VelocityHyperZonePlayer
import icu.h2l.login.vServer.outpre.OutPreState
import icu.h2l.login.vServer.outpre.OutPreVServerAuth
import icu.h2l.login.vServer.outpre.handler.OutPreAuthSessionHandler

/**
 * 需要先进入认证服桥接、再开始等待区认证的模式会话。
 */
internal class BridgeWaitingAreaJoinSession(
    override val player: ConnectedPlayer,
    override val hyperPlayer: VelocityHyperZonePlayer,
    override val handler: OutPreAuthSessionHandler,
    override val state: OutPreState,
    private val shouldPublishAuthStartAfterJoin: Boolean,
) : OutPreInitialJoinSession {
    override fun begin(owner: OutPreVServerAuth) {
        owner.trace(
            "beginInitialJoin connect-bridge channel=${player.getChannel().id()} player=${player.username} ${
                owner.describeState(state)
            }"
        )
        owner.startAuthBridgeJoin(player, hyperPlayer, handler.bridge, state)
    }

    override fun onVerified(owner: OutPreVServerAuth) {
        if (!state.hasConnectedToAuthServerOnce) {
            state.verifiedExitPending = true
            owner.trace(
                "outpre.onVerified deferred-until-auth-join channel=${player.getChannel().id()} player=${player.username} ${
                    owner.describeState(state)
                }"
            )
            return
        }
        owner.continueVerifiedInitialJoin(player, handler, state)
    }

    override fun onAuthServerJoined(owner: OutPreVServerAuth) {
        state.hasConnectedToAuthServerOnce = true
        owner.trace(
            "outpre.onAuthServerJoined channel=${player.getChannel().id()} player=${player.username} waitingArea=${hyperPlayer.isInWaitingArea()} verified=${hyperPlayer.isVerified()} attachedProfile=${hyperPlayer.hasAttachedProfile()} ${
                owner.describeState(state)
            }"
        )
        hyperPlayer.resumeMessageDelivery()
        if (shouldPublishAuthStartAfterJoin) {
            owner.publishAuthStartEvent(player, hyperPlayer, state, "onAuthServerJoined")
        }
        owner.publishWaitingAreaJoinEvent(player, hyperPlayer)

        if (state.verifiedExitPending) {
            state.verifiedExitPending = false
            owner.trace(
                "outpre.onAuthServerJoined consume-verifiedExitPending channel=${player.getChannel().id()} player=${player.username} ${
                    owner.describeState(state)
                }"
            )
            owner.continueVerifiedInitialJoin(player, handler, state)
        }
    }
}
