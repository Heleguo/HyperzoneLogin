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

import com.velocitypowered.proxy.protocol.MinecraftPacket
import com.velocitypowered.proxy.protocol.packet.AvailableCommandsPacket
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OutPreBackendBridgeSessionHandlerTest {
    @Test
    fun `outpre backend bridge drops player info packets directly`() {
        assertTrue(shouldDropOutPreBackendPacket(mockk<UpsertPlayerInfoPacket>(relaxed = true)))
    }

    @Test
    fun `outpre backend bridge keeps non player info packets flowing`() {
        assertFalse(shouldDropOutPreBackendPacket(mockk<AvailableCommandsPacket>(relaxed = true)))
        assertFalse(shouldDropOutPreBackendPacket(mockk<MinecraftPacket>(relaxed = true)))
    }
}
