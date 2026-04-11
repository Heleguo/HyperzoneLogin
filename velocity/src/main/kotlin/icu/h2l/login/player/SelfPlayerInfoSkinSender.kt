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

package icu.h2l.login.player

import com.velocitypowered.api.network.ProtocolVersion
import com.velocitypowered.api.util.GameProfile
import com.velocitypowered.proxy.connection.client.ConnectedPlayer
import com.velocitypowered.proxy.protocol.packet.LegacyPlayerListItemPacket
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket
import icu.h2l.api.log.debug
import java.util.EnumSet

/**
 * 为 self `ADD_PLAYER` 替换链路构造替换包。
 *
 * 注意：
 * 1. 调用方必须先确认当前拦截到的就是“原始 self ADD_PLAYER”；
 * 2. 这里只负责把原包中的附加字段保留下来，并把 profile 本体替换为目标 profile；
 * 3. 目标 profile 必须已经使用固定的 `clientSendUUID` / `clientSendName`；
 * 4. `textures` 必须直接来自缓存的 `pendingSelfSkinTextures`，不能擅自把 signature 置空。
 */
internal object SelfPlayerInfoSkinSender {
	fun sendAddPlayer(player: ConnectedPlayer, profile: GameProfile) {
		val protocolVersion = player.protocolVersion
		if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_19_3)) {
			player.connection.write(createModernAddPlayer(profile))
			return
		}

		if (protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
			player.connection.write(createLegacyAddPlayer(profile))
			return
		}

		debug {
			"[ProfileSkinFlow] self ADD_PLAYER skipped: unsupported protocol for skin properties, player=${player.username}, protocol=$protocolVersion"
		}
	}

	fun createModernReplacement(
		originalPacket: UpsertPlayerInfoPacket,
		originalEntry: UpsertPlayerInfoPacket.Entry,
		replacementProfile: GameProfile
	): UpsertPlayerInfoPacket {
		val replacementEntry = UpsertPlayerInfoPacket.Entry(replacementProfile.id)
		replacementEntry.setProfile(replacementProfile)

		if (originalPacket.containsAction(UpsertPlayerInfoPacket.Action.INITIALIZE_CHAT)) {
			replacementEntry.setChatSession(originalEntry.chatSession)
		}
		if (originalPacket.containsAction(UpsertPlayerInfoPacket.Action.UPDATE_GAME_MODE)) {
			replacementEntry.setGameMode(originalEntry.gameMode)
		}
		if (originalPacket.containsAction(UpsertPlayerInfoPacket.Action.UPDATE_LISTED)) {
			replacementEntry.setListed(originalEntry.isListed)
		}
		if (originalPacket.containsAction(UpsertPlayerInfoPacket.Action.UPDATE_LATENCY)) {
			replacementEntry.setLatency(originalEntry.latency)
		}
		if (originalPacket.containsAction(UpsertPlayerInfoPacket.Action.UPDATE_DISPLAY_NAME)) {
			replacementEntry.setDisplayName(originalEntry.displayName)
		}
		if (originalPacket.containsAction(UpsertPlayerInfoPacket.Action.UPDATE_LIST_ORDER)) {
			replacementEntry.setListOrder(originalEntry.listOrder)
		}
		if (originalPacket.containsAction(UpsertPlayerInfoPacket.Action.UPDATE_HAT)) {
			replacementEntry.setShowHat(originalEntry.isShowHat)
		}

		return UpsertPlayerInfoPacket(EnumSet.copyOf(originalPacket.actions), listOf(replacementEntry))
	}

	private fun createModernAddPlayer(profile: GameProfile): UpsertPlayerInfoPacket {
		val entry = UpsertPlayerInfoPacket.Entry(profile.id)
		entry.setProfile(profile)
		entry.setLatency(0)
		entry.setListed(true)
		return UpsertPlayerInfoPacket(
			EnumSet.of(
				UpsertPlayerInfoPacket.Action.ADD_PLAYER,
				UpsertPlayerInfoPacket.Action.UPDATE_LATENCY,
				UpsertPlayerInfoPacket.Action.UPDATE_LISTED
			),
			listOf(entry)
		)
	}

	fun createModernPassthroughWithoutSelf(
		originalPacket: UpsertPlayerInfoPacket,
		originalSelfProfileId: java.util.UUID
	): UpsertPlayerInfoPacket? {
		val remainingEntries = originalPacket.entries.filter { it.profileId != originalSelfProfileId }
		if (remainingEntries.isEmpty()) {
			return null
		}
		return UpsertPlayerInfoPacket(EnumSet.copyOf(originalPacket.actions), remainingEntries)
	}

	fun createLegacyReplacement(
		originalItem: LegacyPlayerListItemPacket.Item,
		replacementProfile: GameProfile
	): LegacyPlayerListItemPacket {
		val replacementItem = LegacyPlayerListItemPacket.Item(replacementProfile.id)
			.setName(replacementProfile.name)
			.setProperties(replacementProfile.properties)
			.setGameMode(originalItem.gameMode)
			.setLatency(originalItem.latency)
			.setDisplayName(originalItem.displayName)

		originalItem.playerKey?.let(replacementItem::setPlayerKey)

		return LegacyPlayerListItemPacket(
			LegacyPlayerListItemPacket.ADD_PLAYER,
			listOf(replacementItem)
		)
	}

	private fun createLegacyAddPlayer(profile: GameProfile): LegacyPlayerListItemPacket {
		val item = LegacyPlayerListItemPacket.Item(profile.id)
			.setName(profile.name)
			.setProperties(profile.properties)
			.setGameMode(0)
			.setLatency(0)
		return LegacyPlayerListItemPacket(LegacyPlayerListItemPacket.ADD_PLAYER, listOf(item))
	}

	fun createLegacyPassthroughWithoutSelf(
		originalPacket: LegacyPlayerListItemPacket,
		originalSelfProfileId: java.util.UUID
	): LegacyPlayerListItemPacket? {
		val remainingItems = originalPacket.items.filter { it.uuid != originalSelfProfileId }
		if (remainingItems.isEmpty()) {
			return null
		}
		return LegacyPlayerListItemPacket(originalPacket.action, remainingItems)
	}
}


