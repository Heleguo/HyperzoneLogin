package icu.h2l.login.profile.skin.db

import icu.h2l.api.db.table.ProfileTable
import org.jetbrains.exposed.sql.Table

class ProfileSkinCacheTable(prefix: String, profileTable: ProfileTable) : Table("${prefix}profile_skin_cache") {
    val profileId = uuid("profile_id").references(profileTable.id).uniqueIndex()
    val sourceHash = varchar("source_hash", 64).nullable()
    val skinUrl = varchar("skin_url", 1024).nullable()
    val skinModel = varchar("skin_model", 16).nullable()
    val textureValue = text("texture_value")
    val textureSignature = text("texture_signature").nullable()
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(profileId)
}


