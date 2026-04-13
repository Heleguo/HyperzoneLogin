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

package icu.h2l.login.auth.floodgate.credential

import icu.h2l.api.profile.HyperZoneCredential
import java.util.UUID

class FloodgateHyperZoneCredential(
    private val trustedName: String,
    private val trustedUuid: UUID,
    private val knownProfileId: UUID
) : HyperZoneCredential {
    override val channelId: String = CHANNEL_ID
    override val credentialId: String = trustedUuid.toString()

    override fun getBoundProfileId(): UUID {
        return knownProfileId
    }

    override fun validateBind(profileId: UUID): String? {
        if (profileId != knownProfileId) {
            return "Floodgate 凭证 $trustedName($trustedUuid) 已绑定到其他 Profile: $knownProfileId"
        }
        return null
    }

    override fun bind(profileId: UUID): Boolean {
        return profileId == knownProfileId
    }

    fun matches(uuid: UUID): Boolean {
        return trustedUuid == uuid
    }

    companion object {
        private const val CHANNEL_ID = "floodgate"
    }
}

