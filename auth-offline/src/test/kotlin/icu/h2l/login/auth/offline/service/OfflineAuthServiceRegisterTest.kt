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

package icu.h2l.login.auth.offline.service

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import icu.h2l.api.db.Profile
import icu.h2l.api.db.HyperZoneDatabaseManager
import icu.h2l.api.db.table.ProfileTable
import icu.h2l.api.player.HyperZonePlayer
import icu.h2l.api.player.HyperZonePlayerAccessor
import icu.h2l.api.profile.HyperZoneCredential
import icu.h2l.api.profile.HyperZoneProfileService
import icu.h2l.login.auth.offline.OfflineAuthMessages
import icu.h2l.login.auth.offline.api.db.OfflineAuthTable
import icu.h2l.login.auth.offline.config.OfflineAuthConfigLoader
import icu.h2l.login.auth.offline.db.OfflineAuthRepository
import icu.h2l.login.auth.offline.mail.OfflineAuthEmailSender
import icu.h2l.login.auth.offline.totp.OfflineTotpAuthenticator
import io.mockk.*
import java.nio.file.Path
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

class OfflineAuthServiceRegisterTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var repository: OfflineAuthRepository
    private lateinit var player: Player
    private lateinit var hyperZonePlayer: HyperZonePlayer
    private lateinit var proxy: ProxyServer
    private lateinit var profileService: HyperZoneProfileService
    private lateinit var service: OfflineAuthService
    private lateinit var profileTable: ProfileTable
    private lateinit var offlineAuthTable: OfflineAuthTable
    private lateinit var database: Database

    @BeforeEach
    fun setUp() {
        OfflineAuthConfigLoader.load(tempDir)

        database = Database.connect(
            url = "jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        profileTable = ProfileTable()
        offlineAuthTable = OfflineAuthTable("", profileTable)
        val databaseManager = object : HyperZoneDatabaseManager {
            override val tablePrefix: String = ""

            override fun <T> executeTransaction(statement: () -> T): T {
                return transaction(database) { statement() }
            }
        }
        transaction(database) {
            SchemaUtils.create(profileTable, offlineAuthTable)
        }

        repository = OfflineAuthRepository(databaseManager, offlineAuthTable)
        player = mockk(relaxed = true)
        hyperZonePlayer = mockk(relaxUnitFun = true)
        proxy = mockk(relaxed = true)
        profileService = mockk()

        every { hyperZonePlayer.clientOriginalName } returns USERNAME

        service = OfflineAuthService(
            repository = repository,
            playerAccessor = TestPlayerAccessor(hyperZonePlayer),
            profileService = profileService,
            emailSender = NoopEmailSender,
            totpAuthenticator = OfflineTotpAuthenticator("HyperZoneLogin-Test", 5),
            proxy = proxy
        )
    }

    @Test
    fun `register creates a new offline password entry when player can resolve profile`() {
        insertProfile(PROFILE)

        every { profileService.canResolveOrCreateProfile(hyperZonePlayer) } returns true
        every { profileService.resolveOrCreateProfile(hyperZonePlayer, null, null) } returns PROFILE

        val result = service.register(player, VALID_PASSWORD)
        val saved = repository.getByName(NORMALIZED_NAME)

        assertTrue(result.success)
        assertEquals(OfflineAuthMessages.REGISTER_SUCCESS, result.message)
        assertNotNull(saved)
        assertEquals(PROFILE.id, saved!!.profileId)
        assertEquals("sha256", saved.hashFormat)
        assertEquals(64, saved.passwordHash.length)
        assertNotEquals(VALID_PASSWORD, saved.passwordHash)
        verify(exactly = 1) {
            hyperZonePlayer.submitCredential(
                HyperZoneCredential("offline", NORMALIZED_NAME, PROFILE.id)
            )
        }
        verify(exactly = 1) { hyperZonePlayer.overVerify() }
    }

    @Test
    fun `register falls back to binding existing profile when direct registration is unavailable`() {
        insertProfile(PROFILE)

        every { profileService.canResolveOrCreateProfile(hyperZonePlayer) } returns false
        every { hyperZonePlayer.canBind() } returns true
        every { profileService.getAttachedProfile(hyperZonePlayer) } returns PROFILE

        val result = service.register(player, VALID_PASSWORD)
        val saved = repository.getByProfileId(PROFILE.id)

        assertTrue(result.success)
        assertEquals(OfflineAuthMessages.REGISTER_BOUND_SUCCESS, result.message)
        assertNotNull(saved)
        verify(exactly = 1) { profileService.getAttachedProfile(hyperZonePlayer) }
        verify(exactly = 0) { hyperZonePlayer.overVerify() }
    }

    @Test
    fun `register reports denial when neither registration nor binding is allowed`() {
        every { profileService.canResolveOrCreateProfile(hyperZonePlayer) } returns false
        every { hyperZonePlayer.canBind() } returns false

        val result = service.register(player, VALID_PASSWORD)

        assertFalse(result.success)
        assertEquals(OfflineAuthMessages.REGISTER_BIND_DENIED, result.message)
    }

    @Test
    fun `register reports existing offline password when fallback binding target is already linked`() {
        insertProfile(PROFILE)
        repository.create(
            name = NORMALIZED_NAME,
            passwordHash = "existing-hash",
            hashFormat = "sha256",
            profileId = PROFILE.id
        )

        every { profileService.canResolveOrCreateProfile(hyperZonePlayer) } returns false
        every { hyperZonePlayer.canBind() } returns true
        every { profileService.getAttachedProfile(hyperZonePlayer) } returns PROFILE

        val result = service.register(player, VALID_PASSWORD)

        assertFalse(result.success)
        assertEquals(OfflineAuthMessages.OFFLINE_PASSWORD_ALREADY_SET, result.message)
    }

    @Test
    fun `join prompts tell attached players to use register for automatic binding`() {
        insertProfile(PROFILE)

        every { hyperZonePlayer.isInWaitingArea() } returns true
        every { profileService.getAttachedProfile(hyperZonePlayer) } returns PROFILE
        every { profileService.canResolveOrCreateProfile(hyperZonePlayer) } returns false

        val prompts = service.getJoinPrompts(player)

        assertTrue(prompts.contains(OfflineAuthMessages.REGISTER_REQUEST))
        assertTrue(prompts.contains(OfflineAuthMessages.REGISTER_BIND_HINT))
    }

    private fun insertProfile(profile: Profile) {
        transaction(database) {
            profileTable.insert {
                it[id] = profile.id
                it[name] = profile.name
                it[uuid] = profile.uuid
            }
        }
    }

    private object NoopEmailSender : OfflineAuthEmailSender {
        override fun sendRecoveryCode(message: OfflineAuthEmailSender.RecoveryCodeMailMessage): OfflineAuthEmailSender.DeliveryResult {
            return OfflineAuthEmailSender.DeliveryResult(success = true)
        }
    }

    private class TestPlayerAccessor(
        private val hyperZonePlayer: HyperZonePlayer
    ) : HyperZonePlayerAccessor {
        override fun create(
            channel: io.netty.channel.Channel,
            userName: String,
            uuid: UUID,
            isOnline: Boolean
        ): HyperZonePlayer {
            return hyperZonePlayer
        }

        override fun getByPlayer(player: Player): HyperZonePlayer {
            return hyperZonePlayer
        }

        override fun getByChannel(channel: io.netty.channel.Channel): HyperZonePlayer {
            return hyperZonePlayer
        }
    }

    companion object {
        private const val USERNAME = "Alice"
        private const val NORMALIZED_NAME = "alice"
        private const val VALID_PASSWORD = "SecurePass123"
        private val PROFILE = Profile(
            id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            name = USERNAME,
            uuid = UUID.fromString("22222222-2222-2222-2222-222222222222")
        )
    }
}





