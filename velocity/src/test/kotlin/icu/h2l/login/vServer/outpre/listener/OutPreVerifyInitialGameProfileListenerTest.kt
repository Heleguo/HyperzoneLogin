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

import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.util.GameProfile
import com.velocitypowered.proxy.connection.MinecraftConnection
import com.velocitypowered.proxy.connection.client.ConnectedPlayer
import com.velocitypowered.proxy.connection.client.InitialInboundConnection
import icu.h2l.api.HyperZoneApi
import icu.h2l.api.event.profile.VerifyInitialGameProfileEvent
import icu.h2l.api.vServer.HyperZoneVServerAdapter
import icu.h2l.login.HyperZoneLoginMain
import icu.h2l.login.vServer.outpre.OutPreVServerAuth
import io.mockk.every
import io.mockk.mockk
import io.netty.channel.Channel
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import java.util.UUID

class OutPreVerifyInitialGameProfileListenerTest {
    private val listener = OutPreVerifyInitialGameProfileListener()

    @Test
    fun `listener bypasses only the armed outpre verify profile`() {
        val main = bootstrapMain()
        val adapter = OutPreVServerAuth(mockk<ProxyServer>(relaxed = true))
        val channel = mockk<Channel>(relaxed = true)
        val expectedProfile = GameProfile(TEST_UUID, "OutPreUser", listOf(GameProfile.Property("textures", "value", "sig")))
        main.activeVServerAdapter = adapter
        adapter.expectInitialVerifyProfile(player(channel), expectedProfile)
        val event = verifyEvent(channel, GameProfile(TEST_UUID, "OutPreUser", listOf(GameProfile.Property("textures", "value", "sig"))))

        listener.onVerifyInitialGameProfileEvent(event)

        assertTrue(event.pass)
    }

    @Test
    fun `listener keeps verify gate closed for profile mismatch even in outpre mode`() {
        val main = bootstrapMain()
        val adapter = OutPreVServerAuth(mockk<ProxyServer>(relaxed = true))
        val channel = mockk<Channel>(relaxed = true)
        main.activeVServerAdapter = adapter
        adapter.expectInitialVerifyProfile(player(channel), GameProfile(TEST_UUID, "ExpectedUser", emptyList()))
        val event = verifyEvent(channel, GameProfile(TEST_UUID, "ActualUser", emptyList()))

        listener.onVerifyInitialGameProfileEvent(event)

        assertFalse(event.pass)
    }

    @Test
    fun `listener keeps verify gate closed when outpre has no armed profile`() {
        val main = bootstrapMain()
        main.activeVServerAdapter = OutPreVServerAuth(mockk<ProxyServer>(relaxed = true))
        val event = verifyEvent(mockk<Channel>(relaxed = true), GameProfile(TEST_UUID, "OutPreUser", emptyList()))

        listener.onVerifyInitialGameProfileEvent(event)

        assertFalse(event.pass)
    }

    @Test
    fun `listener consumes armed outpre verify profile after first pass`() {
        val main = bootstrapMain()
        val adapter = OutPreVServerAuth(mockk<ProxyServer>(relaxed = true))
        val channel = mockk<Channel>(relaxed = true)
        val expectedProfile = GameProfile(TEST_UUID, "OutPreUser", emptyList())
        main.activeVServerAdapter = adapter
        adapter.expectInitialVerifyProfile(player(channel), expectedProfile)
        val firstEvent = verifyEvent(channel, expectedProfile)
        val secondEvent = verifyEvent(channel, expectedProfile)

        listener.onVerifyInitialGameProfileEvent(firstEvent)
        listener.onVerifyInitialGameProfileEvent(secondEvent)

        assertTrue(firstEvent.pass)
        assertFalse(secondEvent.pass)
    }

    @Test
    fun `listener keeps verify gate closed for non outpre adapters`() {
        val main = bootstrapMain()
        main.activeVServerAdapter = mockk<HyperZoneVServerAdapter>(relaxed = true)
        val event = verifyEvent(mockk<Channel>(relaxed = true), GameProfile(TEST_UUID, "OutPreUser", emptyList()))

        listener.onVerifyInitialGameProfileEvent(event)

        assertFalse(event.pass)
    }

    private fun bootstrapMain(): HyperZoneLoginMain {
        return HyperZoneLoginMain(
            server = mockk(relaxed = true),
            logger = mockk<ComponentLogger>(relaxed = true),
            dataDirectory = Paths.get("build", "tmp", "outpre-verify-listener-test"),
            plugin = mockk<HyperZoneApi>(relaxed = true),
        )
    }

    private fun player(channel: Channel): ConnectedPlayer {
        val player = mockk<ConnectedPlayer>(relaxed = true)
        val connection = mockk<MinecraftConnection>(relaxed = true)
        every { connection.channel } returns channel
        every { player.connection } returns connection
        return player
    }

    private fun verifyEvent(channel: Channel, profile: GameProfile): VerifyInitialGameProfileEvent {
        val minecraftConnection = mockk<MinecraftConnection>(relaxed = true)
        every { minecraftConnection.channel } returns channel
        val connection = mockk<InitialInboundConnection>(relaxed = true)
        every { connection.connection } returns minecraftConnection

        return VerifyInitialGameProfileEvent(
            connection = connection,
            gameProfile = profile,
        )
    }

    companion object {
        private val TEST_UUID: UUID = UUID.fromString("44444444-4444-4444-8444-444444444444")
    }
}



