package icu.h2l.login.auth.online.db

import icu.h2l.login.auth.online.DatabaseManager
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID
import java.util.logging.Logger

/**
 * Entry 表数据操作帮助类
 */
class EntryDatabaseHelper(
    private val databaseManager: DatabaseManager,
    private val logger: Logger
) {
    fun findEntryByNameAndUuid(entryId: String, name: String, uuid: UUID): UUID? {
        val entryTable = databaseManager.getEntryTable(entryId) ?: return null

        return databaseManager.executeTransaction {
            entryTable.selectAll().where { (entryTable.name eq name) and (entryTable.uuid eq uuid) }
                .map { it[entryTable.pid] }
                .firstOrNull()
        }
    }

    fun createEntry(entryId: String, name: String, uuid: UUID, pid: UUID): Boolean {
        val entryTable = databaseManager.getEntryTable(entryId) ?: return false

        return try {
            databaseManager.executeTransaction {
                entryTable.insert {
                    it[entryTable.name] = name
                    it[entryTable.uuid] = uuid
                    it[entryTable.pid] = pid
                }
            }
            true
        } catch (e: Exception) {
            logger.warning("创建入口记录失败: ${e.message}")
            false
        }
    }

    fun updateEntryName(entryId: String, oldUuid: UUID, newName: String): Boolean {
        val entryTable = databaseManager.getEntryTable(entryId) ?: return false

        return try {
            databaseManager.executeTransaction {
                entryTable.update({ entryTable.uuid eq oldUuid }) {
                    it[name] = newName
                }
            } > 0
        } catch (e: Exception) {
            logger.warning("更新入口名称失败: ${e.message}")
            false
        }
    }

    fun verifyEntry(entryId: String, name: String, uuid: UUID): Boolean {
        val entryTable = databaseManager.getEntryTable(entryId) ?: return false

        return databaseManager.executeTransaction {
            entryTable.selectAll().where { (entryTable.name eq name) and (entryTable.uuid eq uuid) }.count() > 0
        }
    }
}
