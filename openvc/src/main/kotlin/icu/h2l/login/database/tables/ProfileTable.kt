package icu.h2l.login.database.tables

import org.jetbrains.exposed.sql.Table

/**
 * 游戏内档案表
 * 存储玩家的游戏内档案信息
 * name和UUID需要保持整个库中无重复
 * 
 * @param prefix 表名前缀，默认为空字符串
 */
class ProfileTable(prefix: String = "") : Table("${prefix}hyperzone_login_profile") {
    /**
     * 档案ID（主键）
     * 用作和入口关联的映射
     */
    val id = uuid("id")
    
    /**
     * 游戏内名称
     */
    val name = varchar("name", 32).uniqueIndex()
    
    /**
     * 游戏内UUID
     */
    val uuid = uuid("uuid").uniqueIndex()
    
    override val primaryKey = PrimaryKey(id)
}
