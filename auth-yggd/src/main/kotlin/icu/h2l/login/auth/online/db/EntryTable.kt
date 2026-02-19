package icu.h2l.login.auth.online.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * 入口表基类
 * 用于不同的登录入口（如 mojang、offline 等）
 * 
 * @param entryId 入口ID，全小写，如 "mojang"、"offline"
 * @param prefix 表名前缀，默认为空字符串
 * @param profileTable 档案表实例，用于外键关联
 */
class EntryTable(entryId: String, prefix: String = "", profileTable: ProfileTable) : Table("${prefix}hyperzone_login_entry_$entryId") {
    /**
     * 自增ID
     */
    val id = integer("id").autoIncrement()
    
    /**
     * 入口处的玩家名
     */
    val name = varchar("name", 32)
    
    /**
     * 入口处的UUID
     */
    val uuid = uuid("uuid")
    
    /**
     * 映射的档案ID（外键）
     */
    val pid = uuid("pid").references(profileTable.id)
    
    override val primaryKey = PrimaryKey(id)
    
    init {
        // 确保 name + uuid 的组合是唯一的
        uniqueIndex(name, uuid)
    }
}
