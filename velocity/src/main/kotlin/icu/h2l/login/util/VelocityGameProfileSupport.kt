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

package icu.h2l.login.util

import com.velocitypowered.api.util.GameProfile
import com.velocitypowered.proxy.VelocityServer
import com.velocitypowered.proxy.connection.client.ConnectedPlayer
import com.velocitypowered.proxy.server.VelocityRegisteredServer
import icu.h2l.api.db.Profile
import java.lang.reflect.Field
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal fun buildDeliveredGameProfile(
    currentGameProfile: GameProfile,
    attachedProfile: Profile,
    enableNameHotChange: Boolean,
    enableUuidHotChange: Boolean,
): GameProfile {
    val resolvedName = if (enableNameHotChange) attachedProfile.name else currentGameProfile.name
    val resolvedUuid = if (enableUuidHotChange) attachedProfile.uuid else currentGameProfile.id
    return GameProfile(resolvedUuid, resolvedName, currentGameProfile.properties)
}

internal fun buildAttachedIdentityGameProfile(
    currentGameProfile: GameProfile,
    attachedProfile: Profile,
): GameProfile {
    return GameProfile(attachedProfile.uuid, attachedProfile.name, currentGameProfile.properties)
}

internal fun hasSemanticGameProfileDifference(expected: GameProfile, actual: GameProfile): Boolean {
    if (expected.id != actual.id || expected.name != actual.name) {
        return true
    }

    return normalizeGameProfileProperties(expected) != normalizeGameProfileProperties(actual)
}

internal fun describeGameProfileBrief(profile: GameProfile): String {
    val propertyNames = profile.properties
        .map { it.name }
        .distinct()
        .sorted()
    return "id=${profile.id}, name=${profile.name}, propertyCount=${profile.properties.size}, propertyNames=$propertyNames"
}

private fun normalizeGameProfileProperties(profile: GameProfile): List<NormalizedGameProfileProperty> {
    return profile.properties
        .map { property ->
            NormalizedGameProfileProperty(
                name = property.name,
                value = property.value,
                signature = property.signature,
            )
        }
        .sortedWith(
            compareBy<NormalizedGameProfileProperty> { it.name }
                .thenBy { it.value }
                .thenBy { it.signature ?: "" }
        )
}

private data class NormalizedGameProfileProperty(
    val name: String,
    val value: String,
    val signature: String?,
)

internal fun setConnectedPlayerGameProfile(player: ConnectedPlayer, profile: GameProfile) {
    VelocityGameProfileReflection.profileField.set(player, profile)
}

internal fun <T> executeOnPlayerEventLoop(player: ConnectedPlayer, action: () -> T): T {
    val eventLoop = player.connection.eventLoop()
    if (eventLoop.inEventLoop()) {
        return action()
    }

    val future = CompletableFuture<T>()
    eventLoop.execute {
        runCatching(action)
            .onSuccess(future::complete)
            .onFailure(future::completeExceptionally)
    }
    return future.join()
}

internal object VelocityGameProfileReflection {
    val profileField: Field = ConnectedPlayer::class.java.getDeclaredField("profile").apply {
        isAccessible = true
    }
    private val connectionsByNameField: Field = VelocityServer::class.java.getDeclaredField("connectionsByName").apply {
        isAccessible = true
    }
    private val connectionsByUuidField: Field = VelocityServer::class.java.getDeclaredField("connectionsByUuid").apply {
        isAccessible = true
    }
    private val registeredServerPlayersField: Field = VelocityRegisteredServer::class.java.getDeclaredField("players").apply {
        isAccessible = true
    }

    @Suppress("UNCHECKED_CAST")
    fun connectionsByName(server: VelocityServer): MutableMap<String, ConnectedPlayer> {
        return connectionsByNameField.get(server) as MutableMap<String, ConnectedPlayer>
    }

    @Suppress("UNCHECKED_CAST")
    fun connectionsByUuid(server: VelocityServer): MutableMap<UUID, ConnectedPlayer> {
        return connectionsByUuidField.get(server) as MutableMap<UUID, ConnectedPlayer>
    }

    @Suppress("UNCHECKED_CAST")
    fun players(server: VelocityRegisteredServer): MutableMap<UUID, ConnectedPlayer> {
        return registeredServerPlayersField.get(server) as MutableMap<UUID, ConnectedPlayer>
    }
}
