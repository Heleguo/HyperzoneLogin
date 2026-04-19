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

package icu.h2l.login.profile

import icu.h2l.api.profile.HyperZoneCredential
import java.util.*

data class PendingProfileCreateContext(
    val suggestedUuid: UUID?,
    val hasUnboundCredentials: Boolean
)

fun resolvePendingProfileCreateContext(credentials: List<HyperZoneCredential>): PendingProfileCreateContext? {
    val unboundCredentials = credentials.filter { it.getBoundProfileId() == null }
    if (unboundCredentials.isEmpty()) {
        return PendingProfileCreateContext(
            suggestedUuid = null,
            hasUnboundCredentials = false
        )
    }

    val distinctSuggestions = unboundCredentials
        .map { it.getSuggestedProfileCreateUuid() }
        .distinct()
    if (distinctSuggestions.size > 1) {
        return null
    }

    return PendingProfileCreateContext(
        suggestedUuid = distinctSuggestions.singleOrNull(),
        hasUnboundCredentials = true
    )
}

