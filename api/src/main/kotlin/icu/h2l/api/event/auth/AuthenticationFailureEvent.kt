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

package icu.h2l.api.event.auth

class AuthenticationFailureEvent(
    val userName: String,
    val playerIp: String?,
    val authType: AuthType,
    val reason: Reason,
    val reasonMessage: String,
    val providerId: String? = null,
    val throwableSummary: String? = null,
    val occurredAt: Long = System.currentTimeMillis()
) {
    enum class AuthType {
        OFFLINE,
        YGGDRASIL
    }

    enum class Reason {
        INVALID_CREDENTIALS,
        RATE_LIMITED,
        TOTP_REQUIRED,
        TOTP_INVALID,
        SESSION_REJECTED,
        REMOTE_REJECTED,
        TIMEOUT,
        NO_PROVIDERS,
        PLAYER_STATE_REJECTED,
        UNKNOWN
    }
}

