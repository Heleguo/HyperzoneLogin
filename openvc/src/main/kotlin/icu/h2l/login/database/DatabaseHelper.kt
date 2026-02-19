package icu.h2l.login.database

import icu.h2l.login.auth.online.db.EntryDatabaseHelper
import icu.h2l.login.manager.DatabaseManager
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID
import java.util.logging.Logger

/**
 * 数据库操作示例和帮助类
 */
class DatabaseHelper(
    private val manager: DatabaseManager,
    private val logger: Logger
) {
    
    private val profileTable = manager.getProfileTable()
    private val entryHelper = EntryDatabaseHelper(manager, logger)
    
    /**
     * 根据 name 或 uuid 查找档案（OR 查询）
     * 
     * @param name 玩家名
     * @param uuid 玩家UUID
     * @return 档案ID，如果不存在返回 null
     */
    fun findProfileByNameOrUuid(name: String, uuid: UUID): UUID? {
        return manager.executeTransaction {
            profileTable.selectAll().where { (profileTable.name eq name) or (profileTable.uuid eq uuid) }.limit(1).map { it[profileTable.id] }.firstOrNull()
        }
    }
    
    /**
     * 创建新的游戏档案
     * 
     * @param id 档案ID
     * @param name 玩家名
     * @param uuid 玩家UUID
     * @return 是否创建成功
     */
    fun createProfile(id: UUID, name: String, uuid: UUID): Boolean {
        return try {
            manager.executeTransaction {
                profileTable.insert {
                    it[profileTable.id] = id
                    it[profileTable.name] = name
                    it[profileTable.uuid] = uuid
                }
            }
            true
        } catch (e: Exception) {
            logger.warning("创建档案失败: ${e.message}")
            false
        }
    }
    
    /**
     * 更新档案名称
     * 
     * @param profileId 档案ID
     * @param newName 新名称
     * @return 是否更新成功
     */
    fun updateProfileName(profileId: UUID, newName: String): Boolean {
        return try {
            manager.executeTransaction {
                profileTable.update({ profileTable.id eq profileId }) {
                    it[name] = newName
                }
            } > 0
        } catch (e: Exception) {
            logger.warning("更新档案名称失败: ${e.message}")
            false
        }
    }
    
    /**
     * 在入口表中查找记录
     * 
     * @param entryId 入口ID
     * @param name 玩家名
     * @param uuid 玩家UUID
     * @return 档案ID（pid），如果不存在返回 null
     */
    fun findEntryByNameAndUuid(entryId: String, name: String, uuid: UUID): UUID? {
        return entryHelper.findEntryByNameAndUuid(entryId, name, uuid)
    }
    
    /**
     * 创建入口记录
     * 
     * @param entryId 入口ID
     * @param name 玩家名
     * @param uuid 玩家UUID
     * @param pid 档案ID
     * @return 是否创建成功
     */
    fun createEntry(entryId: String, name: String, uuid: UUID, pid: UUID): Boolean {
        return entryHelper.createEntry(entryId, name, uuid, pid)
    }
    
    /**
     * 更新入口表中的name
     * 
     * @param entryId 入口ID
     * @param oldUuid 原UUID
     * @param newName 新名称
     * @return 是否更新成功
     */
    fun updateEntryName(entryId: String, oldUuid: UUID, newName: String): Boolean {
        return entryHelper.updateEntryName(entryId, oldUuid, newName)
    }
    
    /**
     * 检查入口表中的记录是否与给定的 name 和 uuid 匹配
     * 
     * @param entryId 入口ID
     * @param name 玩家名
     * @param uuid 玩家UUID
     * @return true 如果匹配，false 如果不匹配
     */
    fun verifyEntry(entryId: String, name: String, uuid: UUID): Boolean {
        return entryHelper.verifyEntry(entryId, name, uuid)
    }
    
    /**
     * 获取档案信息
     * 
     * @param profileId 档案ID
     * @return 档案信息（name, uuid），如果不存在返回 null
     */
    fun getProfile(profileId: UUID): Pair<String, UUID>? {
        return manager.executeTransaction {
            profileTable.selectAll().where { profileTable.id eq profileId }
                .map { it[profileTable.name] to it[profileTable.uuid] }
                .firstOrNull()
        }
    }
}
