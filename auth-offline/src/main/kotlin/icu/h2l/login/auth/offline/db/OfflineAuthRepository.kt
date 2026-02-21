package icu.h2l.login.auth.offline.db

import icu.h2l.api.db.HyperZoneDatabaseManager
import icu.h2l.api.log.warn
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.UUID

class OfflineAuthRepository(
    private val databaseManager: HyperZoneDatabaseManager,
    private val table: OfflineAuthTable
) {
    fun create(name: String, passwordHash: String, hashFormat: String, profileId: UUID): Boolean {
        return try {
            databaseManager.executeTransaction {
                table.insert {
                    it[this.name] = name
                    it[this.passwordHash] = passwordHash
                    it[this.hashFormat] = hashFormat
                    it[this.profileId] = profileId
                }
            }
            true
        } catch (e: Exception) {
            warn { "创建离线认证记录失败: ${e.message}" }
            false
        }
    }

    fun getByName(name: String): OfflineAuthEntry? {
        return databaseManager.executeTransaction {
            table.selectAll().where { table.name eq name }
                .limit(1)
                .map { row ->
                    OfflineAuthEntry(
                        id = row[table.id],
                        name = row[table.name],
                        passwordHash = row[table.passwordHash],
                        hashFormat = row[table.hashFormat],
                        profileId = row[table.profileId]
                    )
                }
                .firstOrNull()
        }
    }

    fun getByProfileId(profileId: UUID): OfflineAuthEntry? {
        return databaseManager.executeTransaction {
            table.selectAll().where { table.profileId eq profileId }
                .limit(1)
                .map { row ->
                    OfflineAuthEntry(
                        id = row[table.id],
                        name = row[table.name],
                        passwordHash = row[table.passwordHash],
                        hashFormat = row[table.hashFormat],
                        profileId = row[table.profileId]
                    )
                }
                .firstOrNull()
        }
    }

    fun updatePassword(profileId: UUID, passwordHash: String, hashFormat: String): Boolean {
        return try {
            databaseManager.executeTransaction {
                table.update({ table.profileId eq profileId }) {
                    it[this.passwordHash] = passwordHash
                    it[this.hashFormat] = hashFormat
                }
            } > 0
        } catch (e: Exception) {
            warn { "更新离线认证密码失败: ${e.message}" }
            false
        }
    }

    fun deleteByProfileId(profileId: UUID): Boolean {
        return try {
            databaseManager.executeTransaction {
                table.deleteWhere { table.profileId eq profileId }
            } > 0
        } catch (e: Exception) {
            warn { "删除离线认证记录失败: ${e.message}" }
            false
        }
    }
}