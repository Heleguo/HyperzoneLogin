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

package icu.h2l.login.profile.skin.db

import icu.h2l.api.db.HyperZoneDatabaseManager
import icu.h2l.api.log.warn
import icu.h2l.api.profile.skin.ProfileSkinSource
import icu.h2l.api.profile.skin.ProfileSkinTextures
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

data class ProfileSkinCacheRecord(
    val profileId: UUID,
    val sourceHash: String?,
    val sourceCacheEligible: Boolean?,
    val skinUrl: String?,
    val skinModel: String?,
    val textures: ProfileSkinTextures,
    val updatedAt: Long
)

internal fun isEligibleForSourceCache(record: ProfileSkinCacheRecord): Boolean {
    return record.sourceCacheEligible != false
}

internal fun hasSourceChanged(
    existing: ProfileSkinCacheRecord,
    source: ProfileSkinSource?,
    sourceHash: String?
): Boolean {
    val newUrl = source?.skinUrl
    val newModel = source?.model

    if (newUrl.isNullOrBlank() && sourceHash.isNullOrBlank()) {
        return false
    }

    if (source != null && sourceHash != existing.sourceHash) {
        return true
    }

    if (!newUrl.isNullOrBlank() && newUrl != existing.skinUrl) {
        return true
    }

    if (!newModel.isNullOrBlank() && newModel != existing.skinModel) {
        return true
    }

    return false
}

internal fun hasTexturesChanged(
    existing: ProfileSkinCacheRecord,
    textures: ProfileSkinTextures
): Boolean {
    return existing.textures.value != textures.value || existing.textures.signature != textures.signature
}

internal fun hasSourceCacheEligibilityChanged(
    existing: ProfileSkinCacheRecord,
    sourceCacheEligible: Boolean
): Boolean {
    return existing.sourceCacheEligible != sourceCacheEligible
}

internal fun shouldSkipSave(
    existing: ProfileSkinCacheRecord?,
    source: ProfileSkinSource?,
    textures: ProfileSkinTextures,
    sourceHash: String?,
    sourceCacheEligible: Boolean
): Boolean {
    if (existing == null) {
        return false
    }
    return !hasSourceChanged(existing, source, sourceHash)
            && !hasTexturesChanged(existing, textures)
            && !hasSourceCacheEligibilityChanged(existing, sourceCacheEligible)
}

class ProfileSkinCacheRepository(
    private val databaseManager: HyperZoneDatabaseManager,
    private val table: ProfileSkinCacheTable
) {
    enum class SaveResult {
        INSERTED,
        UPDATED,
        SKIPPED
    }

    fun findByProfileId(profileId: UUID): ProfileSkinCacheRecord? {
        return databaseManager.executeTransaction {
            table.selectAll().where { table.profileId eq profileId }
                .limit(1)
                .map { row ->
                    ProfileSkinCacheRecord(
                        profileId = row[table.profileId],
                        sourceHash = row[table.sourceHash],
                        sourceCacheEligible = row[table.sourceCacheEligible],
                        skinUrl = row[table.skinUrl],
                        skinModel = row[table.skinModel],
                        textures = ProfileSkinTextures(
                            value = row[table.textureValue],
                            signature = row[table.textureSignature]
                        ),
                        updatedAt = row[table.updatedAt]
                    )
                }
                .firstOrNull()
        }
    }

    fun findBySourceHash(sourceHash: String): ProfileSkinCacheRecord? {
        return databaseManager.executeTransaction {
            table.selectAll().where { table.sourceHash eq sourceHash }
                .map { row ->
                    ProfileSkinCacheRecord(
                        profileId = row[table.profileId],
                        sourceHash = row[table.sourceHash],
                        sourceCacheEligible = row[table.sourceCacheEligible],
                        skinUrl = row[table.skinUrl],
                        skinModel = row[table.skinModel],
                        textures = ProfileSkinTextures(
                            value = row[table.textureValue],
                            signature = row[table.textureSignature]
                        ),
                        updatedAt = row[table.updatedAt]
                    )
                }
                .filter(::isEligibleForSourceCache)
                .maxByOrNull(ProfileSkinCacheRecord::updatedAt)
        }
    }

    fun save(
        profileId: UUID,
        source: ProfileSkinSource?,
        textures: ProfileSkinTextures,
        sourceHash: String?,
        sourceCacheEligible: Boolean
    ): SaveResult {
        val existing = findByProfileId(profileId)
        if (shouldSkipSave(existing, source, textures, sourceHash, sourceCacheEligible)) {
            return SaveResult.SKIPPED
        }

        if (existing == null) {
            return try {
                databaseManager.executeTransaction {
                    table.insert {
                        it[table.profileId] = profileId
                        it[table.sourceHash] = sourceHash
                        it[table.sourceCacheEligible] = sourceCacheEligible
                        it[skinUrl] = source?.skinUrl
                        it[skinModel] = source?.model
                        it[textureValue] = textures.value
                        it[textureSignature] = textures.signature
                        it[updatedAt] = System.currentTimeMillis()
                    }
                }
                SaveResult.INSERTED
            } catch (e: Exception) {
                warn { "写入皮肤缓存失败: ${e.message}" }
                SaveResult.SKIPPED
            }
        }

        val updated = try {
            databaseManager.executeTransaction {
                table.update({ table.profileId eq profileId }) {
                    it[table.sourceHash] = sourceHash
                        it[table.sourceCacheEligible] = sourceCacheEligible
                    it[skinUrl] = source?.skinUrl
                    it[skinModel] = source?.model
                    it[textureValue] = textures.value
                    it[textureSignature] = textures.signature
                    it[updatedAt] = System.currentTimeMillis()
                }
            } > 0
        } catch (e: Exception) {
            warn { "更新皮肤缓存失败: ${e.message}" }
            false
        }

        if (updated) {
            return SaveResult.UPDATED
        }

        return SaveResult.SKIPPED
    }
}

