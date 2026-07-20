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

package icu.h2l.login.vServer.outpre

internal data class OutPreState(
    var authTargetLabel: String,
    var returnTargetServerName: String? = null,
    var inAuthHold: Boolean = true,
    var hasConnectedToAuthServerOnce: Boolean = false,
    var verifiedExitPending: Boolean = false,
    var initialFlowPending: Boolean = false,
)

internal sealed interface OutPreInitialJoinFlow {
    val requiresAuthServerBridge: Boolean

    fun createState(authTargetLabel: String): OutPreState

    fun onStatePrepared(fireAuthStart: () -> Unit) = Unit

    fun onAuthServerJoined(fireAuthStart: () -> Unit) = Unit
}

internal object WaitingAreaServerJoinFlow : OutPreInitialJoinFlow {
    override val requiresAuthServerBridge: Boolean = true

    override fun createState(authTargetLabel: String): OutPreState {
        return OutPreState(
            authTargetLabel = authTargetLabel,
            initialFlowPending = true,
            inAuthHold = true,
            hasConnectedToAuthServerOnce = false,
        )
    }

    override fun onAuthServerJoined(fireAuthStart: () -> Unit) {
        fireAuthStart()
    }
}

internal object StruckInitialJoinFlow : OutPreInitialJoinFlow {
    override val requiresAuthServerBridge: Boolean = false

    override fun createState(authTargetLabel: String): OutPreState {
        return OutPreState(
            authTargetLabel = authTargetLabel,
            initialFlowPending = true,
            inAuthHold = false,
            hasConnectedToAuthServerOnce = true,
        )
    }

    override fun onStatePrepared(fireAuthStart: () -> Unit) {
        fireAuthStart()
    }
}

internal object OutPreInitialJoinFlows {
    fun select(struck: Boolean): OutPreInitialJoinFlow {
        return if (struck) StruckInitialJoinFlow else WaitingAreaServerJoinFlow
    }
}
