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

package icu.h2l.login.auth.online.service

import icu.h2l.api.db.HyperZoneDatabaseManager
import icu.h2l.api.db.table.AuthModeTable
import icu.h2l.api.log.HyperZoneDebugType
import icu.h2l.api.log.debug
import icu.h2l.api.log.info
import icu.h2l.api.player.HyperZonePlayer
import icu.h2l.api.profile.CredentialChannelRegistryProvider
import icu.h2l.api.profile.HyperZoneProfileService
import icu.h2l.login.auth.online.config.EntryConfig
import icu.h2l.login.auth.online.credential.YggdrasilHyperZoneCredential
import icu.h2l.login.auth.online.db.EntryDatabaseHelper
import icu.h2l.login.auth.online.manager.EntryConfigManager
import icu.h2l.login.auth.online.message.YggdrasilMessages
import icu.h2l.login.auth.online.record.YggdrasilCredentialPreparation
import icu.h2l.login.auth.online.record.YggdrasilAuthResult
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.*

class YggdrasilCredentialService(
    private val entryConfigManager: EntryConfigManager,
    private val profileService: HyperZoneProfileService,
    private val entryDatabaseHelper: EntryDatabaseHelper,
    private val databaseManager: HyperZoneDatabaseManager
) {
    fun prepareCredentialForSuccessfulAuth(
        handler: HyperZonePlayer,
        result: YggdrasilAuthResult.Success
    ): YggdrasilCredentialPreparation {
        if (profileService.getAttachedProfile(handler) != null) {
            return YggdrasilCredentialPreparation(credentialToSubmit = null)
        }

        // 检查 auth_mode：如果已绑定非当前 Entry 或 UUID 不匹配，直接拒绝并踢出
        if (isAuthEntryMismatch(result.profile.name, result.entryId, result.profile.id)) {
            val existingAuthType = queryExistingAuthType(result.profile.name)
            val message = when (existingAuthType) {
                "MOJANG" -> "此 ID 已绑定正版（Mojang/Microsoft）认证，请使用正版方式登录。"
                "YGGDRASIL" -> "此 ID 已绑定皮肤站认证，请使用皮肤站方式登录。"
                else -> "此账号已绑定到其他认证来源，无法使用当前 Entry（${result.entryId}）登录"
            }
            info { "玩家 ${result.profile.name} 认证冲突 (auth_type=$existingAuthType, entry=${result.entryId})，拒绝连接" }
            handler.getProxyPlayerOrNull()?.disconnect(Component.text(message, NamedTextColor.RED))
            return YggdrasilCredentialPreparation(
                credentialToSubmit = null,
                failureReason = message
            )
        }

        // 检查 auth_mode：玩家有 OFFLINE 记录时（升级场景），
        // 不创建新 Profile，只记录认证来源 Entry ID，保留玩家在等待区
        if (hasOfflineAuthMode(result.profile.name)) {
            info { "玩家 ${result.profile.name} 存在离线注册记录，Yggdrasil 认证跳过建档，记录 Entry=${result.entryId}" }
            return YggdrasilCredentialPreparation(credentialToSubmit = null)
        }

        val existingBoundProfileId = findBoundProfileIdByAuthenticatedEntry(result)
        if (existingBoundProfileId != null) {
            return YggdrasilCredentialPreparation(
                credentialToSubmit = yggdrasilCredential(
                    entryId = result.entryId,
                    authenticatedName = result.profile.name,
                    authenticatedUuid = result.profile.id,
                    suggestedProfileCreateUuid = resolveProfileResolveUuid(result),
                    knownProfileId = existingBoundProfileId
                )
            )
        }

        val profileResolveUuid = resolveProfileResolveUuid(result)
        val probeCredential = yggdrasilCredential(
            entryId = result.entryId,
            authenticatedName = result.profile.name,
            authenticatedUuid = result.profile.id,
            suggestedProfileCreateUuid = profileResolveUuid
        )

        val channelAbility = CredentialChannelRegistryProvider.getOrNull()?.getChannelAbility("yggdrasil")
        if (channelAbility?.canRegister == false) {
            return YggdrasilCredentialPreparation(
                credentialToSubmit = probeCredential,
                failureReason = YggdrasilMessages.registrationDisabledReason(handler)
            )
        }

        if (profileService.canCreate(probeCredential)) {
            val createdProfile = try {
                profileService.create(probeCredential)
            } catch (throwable: IllegalStateException) {
                return YggdrasilCredentialPreparation(
                    credentialToSubmit = null,
                    failureReason = throwable.message ?: "创建 Profile 失败"
                )
            }

            val bound = entryDatabaseHelper.createEntry(
                entryId = result.entryId,
                name = result.profile.name,
                uuid = result.profile.id,
                pid = createdProfile.id
            )
            if (bound) {
                return YggdrasilCredentialPreparation(
                    credentialToSubmit = yggdrasilCredential(
                        entryId = result.entryId,
                        authenticatedName = result.profile.name,
                        authenticatedUuid = result.profile.id,
                        suggestedProfileCreateUuid = profileResolveUuid,
                        knownProfileId = createdProfile.id
                    )
                )
            }
        }

        return YggdrasilCredentialPreparation(
            credentialToSubmit = yggdrasilCredential(
                entryId = result.entryId,
                authenticatedName = result.profile.name,
                authenticatedUuid = result.profile.id,
                suggestedProfileCreateUuid = profileResolveUuid
            )
        )
    }

    fun findBoundProfileIdByAuthenticatedEntry(success: YggdrasilAuthResult.Success): UUID? {
        val profileId = entryDatabaseHelper.findEntryByUuid(
            entryId = success.entryId,
            uuid = success.profile.id
        ) ?: return null

        entryDatabaseHelper.updateEntryName(
            entryId = success.entryId,
            uuid = success.profile.id,
            newName = success.profile.name
        )

        return profileId
    }

    private fun resolveProfileResolveUuid(result: YggdrasilAuthResult.Success): UUID? {
        val entryConfig = entryConfigManager.getConfigById(result.entryId)
        if (entryConfig == null) {
            debug(HyperZoneDebugType.YGGDRASIL_AUTH) {
                "[YggdrasilFlow] 未找到 Entry ${result.entryId} 的配置，Profile 解析回退为透传远端 UUID: ${result.profile.id}"
            }
            return result.profile.id
        }

        return if (entryConfig.yggdrasil.passYggdrasilUuidToProfileResolve) {
            result.profile.id
        } else {
            null
        }
    }

    private fun yggdrasilCredential(
        entryId: String,
        authenticatedName: String,
        authenticatedUuid: UUID,
        suggestedProfileCreateUuid: UUID?,
        knownProfileId: UUID? = null
    ): YggdrasilHyperZoneCredential {
        return YggdrasilHyperZoneCredential(
            entryDatabaseHelper = entryDatabaseHelper,
            entryId = entryId,
            authenticatedName = authenticatedName,
            authenticatedUUID = authenticatedUuid,
            suggestedProfileCreateUuid = suggestedProfileCreateUuid,
            knownProfileId = knownProfileId
        )
    }

    /**
     * 检查 auth_mode 表：以 player_name 查询已有记录，校验认证来源和 UUID 一致性。
     */
    private fun isAuthEntryMismatch(playerName: String, entryId: String, playerUuid: UUID): Boolean {
        return try {
            val authModeTable = AuthModeTable(databaseManager.tablePrefix)
            databaseManager.executeTransaction {
                val row = authModeTable.selectAll().where {
                    authModeTable.playerName eq playerName
                }.limit(1).singleOrNull() ?: return@executeTransaction false
                val storedUuid = row[authModeTable.playerUuid]
                val storedEntryId = row[authModeTable.authEntryId]
                val authType = row[authModeTable.authType]
                info { "isAuthEntryMismatch: player=$playerName storedUuid=$storedUuid currentUuid=$playerUuid storedEntryId=$storedEntryId currentEntryId=$entryId authType=$authType" }

                // 在线认证（MOJANG/YGGDRASIL）必须比对 UUID
                if (authType == "MOJANG" || authType == "YGGDRASIL") {
                    if (storedUuid != playerUuid) {
                        info { "isAuthEntryMismatch: UUID 不匹配，拒绝连接 player=$playerName storedUuid=$storedUuid currentUuid=$playerUuid authType=$authType" }
                        return@executeTransaction true
                    }
                }

                if (storedEntryId == null) {
                    // 旧记录未存储 auth_entry_id：UUID 已匹配，补录当前 Entry，放行
                    if (authType == "MOJANG" || authType == "YGGDRASIL") {
                        authModeTable.update({ authModeTable.playerUuid eq storedUuid }) {
                            it[authModeTable.authEntryId] = entryId
                        }
                        info { "isAuthEntryMismatch: captured entryId=$entryId for player=$playerName (was null, UUID matched)" }
                    }
                    return@executeTransaction false
                }
                val mismatched = storedEntryId != entryId
                info { "isAuthEntryMismatch: player=$playerName result=$mismatched (stored=$storedEntryId, current=$entryId)" }
                mismatched
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun hasOfflineAuthMode(playerName: String): Boolean {
        return try {
            val authModeTable = AuthModeTable(databaseManager.tablePrefix)
            databaseManager.executeTransaction {
                authModeTable.selectAll().where {
                    authModeTable.playerName eq playerName
                }.limit(1).singleOrNull()?.let { row ->
                    row[authModeTable.authType] == "OFFLINE"
                } ?: false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun queryExistingAuthType(playerName: String): String? {
        return try {
            val authModeTable = AuthModeTable(databaseManager.tablePrefix)
            databaseManager.executeTransaction {
                authModeTable.selectAll().where {
                    authModeTable.playerName eq playerName
                }.limit(1).singleOrNull()?.let { row ->
                    row[authModeTable.authType]
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
