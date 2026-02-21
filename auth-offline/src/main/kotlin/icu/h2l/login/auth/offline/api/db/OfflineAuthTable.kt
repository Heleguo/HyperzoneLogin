package icu.h2l.login.auth.offline.api.db

import icu.h2l.api.db.table.ProfileTable
import org.jetbrains.exposed.sql.Table
import java.util.UUID

data class OfflineAuthEntry(
    val id: Int,
    val name: String,
    val passwordHash: String,
    val hashFormat: String,
    val profileId: UUID
)

class OfflineAuthTable(prefix: String, profileTable: ProfileTable) : Table("${prefix}offline_auth") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 32).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val hashFormat = varchar("hash_format", 32)
    val profileId = uuid("profile_id").references(profileTable.id).uniqueIndex()

    override val primaryKey = PrimaryKey(id)
}