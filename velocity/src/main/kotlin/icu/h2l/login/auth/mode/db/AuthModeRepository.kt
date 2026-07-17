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

package icu.h2l.login.auth.mode.db

import icu.h2l.api.db.HyperZoneDatabaseManager
import icu.h2l.api.log.warn
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.*

data class AuthModeEntry(
    val id: Int,
    val playerUuid: UUID,
    val playerName: String,
    val authType: String,
    val createdAt: Long,
    val updatedAt: Long
)

class AuthModeRepository(
    private val databaseManager: HyperZoneDatabaseManager,
    private val table: AuthModeTable
) {
    fun create(playerUuid: UUID, playerName: String, authType: String): Boolean {
        val now = System.currentTimeMillis()
        return try {
            databaseManager.executeTransaction {
                table.insert {
                    it[this.playerUuid] = playerUuid
                    it[this.playerName] = playerName
                    it[this.authType] = authType
                    it[this.createdAt] = now
                    it[this.updatedAt] = now
                }
            }
            true
        } catch (e: Exception) {
            warn { "创建认证模式记录失败: ${e.message}" }
            false
        }
    }

    fun getByUuid(playerUuid: UUID): AuthModeEntry? {
        return databaseManager.executeTransaction {
            table.selectAll().where { table.playerUuid eq playerUuid }
                .limit(1)
                .map(::toEntry)
                .firstOrNull()
        }
    }

    fun getByName(playerName: String): AuthModeEntry? {
        return databaseManager.executeTransaction {
            table.selectAll().where { table.playerName eq playerName }
                .limit(1)
                .map(::toEntry)
                .firstOrNull()
        }
    }

    fun updateAuthType(playerUuid: UUID, newAuthType: String): Boolean {
        val now = System.currentTimeMillis()
        return try {
            databaseManager.executeTransaction {
                table.update({ table.playerUuid eq playerUuid }) {
                    it[this.authType] = newAuthType
                    it[this.updatedAt] = now
                }
            } > 0
        } catch (e: Exception) {
            warn { "更新认证模式失败: ${e.message}" }
            false
        }
    }

    fun updatePlayerName(playerUuid: UUID, newName: String): Boolean {
        val now = System.currentTimeMillis()
        return try {
            databaseManager.executeTransaction {
                table.update({ table.playerUuid eq playerUuid }) {
                    it[this.playerName] = newName
                    it[this.updatedAt] = now
                }
            } > 0
        } catch (e: Exception) {
            warn { "更新认证模式玩家名失败: ${e.message}" }
            false
        }
    }

    fun deleteByUuid(playerUuid: UUID): Boolean {
        return try {
            databaseManager.executeTransaction {
                table.deleteWhere { table.playerUuid eq playerUuid }
            } > 0
        } catch (e: Exception) {
            warn { "删除认证模式记录失败: ${e.message}" }
            false
        }
    }

    fun existsByName(playerName: String): Boolean {
        return getByName(playerName) != null
    }

    fun existsByUuid(playerUuid: UUID): Boolean {
        return getByUuid(playerUuid) != null
    }

    private fun toEntry(row: org.jetbrains.exposed.sql.ResultRow): AuthModeEntry {
        return AuthModeEntry(
            id = row[table.id],
            playerUuid = row[table.playerUuid],
            playerName = row[table.playerName],
            authType = row[table.authType],
            createdAt = row[table.createdAt],
            updatedAt = row[table.updatedAt]
        )
    }
}
