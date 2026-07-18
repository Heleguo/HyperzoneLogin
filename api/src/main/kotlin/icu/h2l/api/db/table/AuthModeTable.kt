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

package icu.h2l.api.db.table

import org.jetbrains.exposed.sql.Table

/**
 * 玩家认证方式表。
 *
 * 记录每个玩家的认证方式（OFFLINE / MOJANG / YGGDRASIL），
 * 通过 player_uuid 关联主玩家数据表。
 * 该表不设外键引用 profile 表，保持独立方便解耦与迁移。
 *
 * @param prefix 表名前缀
 */
class AuthModeTable(prefix: String = "") : Table("${prefix}auth_mode") {
    val id = integer("id").autoIncrement()
    val playerUuid = uuid("player_uuid")
    val playerName = varchar("player_name", 16)
    val authType = varchar("auth_type", 16).default("OFFLINE")
    val authEntryId = varchar("auth_entry_id", 64).nullable().default(null)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    init {
        uniqueIndex("${tableName}_player_uuid", playerUuid)
        index(false, playerName)
    }

    override val primaryKey = PrimaryKey(id)
}
