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

package icu.h2l.login.vServer.limbo.handler

import com.velocitypowered.api.proxy.Player
import icu.h2l.api.event.vServer.VServerJoinEvent
import icu.h2l.api.player.HyperZonePlayer
import icu.h2l.login.HyperZoneLoginMain
import net.elytrium.limboapi.api.Limbo
import net.elytrium.limboapi.api.LimboSessionHandler
import net.elytrium.limboapi.api.player.LimboPlayer

class LimboAuthSessionHandler(
    private val proxyPlayer: Player,
    private val hyperZonePlayer: HyperZonePlayer,
    private val onSpawned: (Player, HyperZonePlayer, LimboPlayer) -> Unit
) : LimboSessionHandler {

    override fun onSpawn(server: Limbo, player: LimboPlayer) {
        onSpawned(proxyPlayer, hyperZonePlayer, player)
        player.disableFalling()

        HyperZoneLoginMain.getInstance().proxy.eventManager.fire(
            VServerJoinEvent(proxyPlayer, hyperZonePlayer)
        )
    }

    override fun onChat(chat: String?) {
        val input = chat ?: return
        HyperZoneLoginMain.getInstance().chatCommandManager.executeChat(proxyPlayer, input)
    }
}
