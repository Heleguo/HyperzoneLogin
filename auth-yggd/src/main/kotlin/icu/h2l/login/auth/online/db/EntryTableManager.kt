package icu.h2l.login.auth.online.db

import com.velocitypowered.api.event.Subscribe
import `fun`.iiii.h2l.api.db.HyperZoneDatabaseManager
import `fun`.iiii.h2l.api.db.table.ProfileTable
import `fun`.iiii.h2l.api.event.db.TableSchemaAction
import `fun`.iiii.h2l.api.event.db.TableSchemaEvent
import `fun`.iiii.h2l.api.event.db.TableSchemaEventApi
import icu.h2l.login.auth.online.events.EntryRegisterEvent
import org.jetbrains.exposed.sql.SchemaUtils
import java.util.logging.Logger

/**
 * Entry 表管理器
 * 负责 Entry 表的注册、查询、创建、删除与事件处理
 */
class EntryTableManager(
    private val logger: Logger,
    private val databaseManager: HyperZoneDatabaseManager,
    private val tablePrefix: String,
    private val profileTable: ProfileTable
) {
    private val entryTables = mutableMapOf<String, EntryTable>()

    init {
        TableSchemaEventApi.registerListener(::onSchemaEvent)
    }

    fun registerEntry(entryId: String): EntryTable {
        val normalizedId = entryId.lowercase()
        return entryTables.getOrPut(normalizedId) {
            logger.info("注册入口表: $normalizedId")
            EntryTable(normalizedId, tablePrefix, profileTable)
        }
    }

    fun getEntryTable(entryId: String): EntryTable? {
        return entryTables[entryId.lowercase()]
    }

    private fun createAllEntryTables() {
        databaseManager.executeTransaction {
            entryTables.values.forEach { entryTable ->
                SchemaUtils.create(entryTable)
                logger.info("已创建表: ${entryTable.tableName}")
            }
        }
    }

    private fun dropAllEntryTables() {
        databaseManager.executeTransaction {
            entryTables.values.forEach { entryTable ->
                SchemaUtils.drop(entryTable)
                logger.warning("已删除表: ${entryTable.tableName}")
            }
        }
    }

    private fun onSchemaEvent(event: TableSchemaEvent) {
        when (event.action) {
            TableSchemaAction.CREATE_ALL -> createAllEntryTables()
            TableSchemaAction.DROP_ALL -> dropAllEntryTables()
        }
    }

    @Subscribe
    fun onEntryRegister(event: EntryRegisterEvent) {
        logger.info("接收到 Entry 注册事件: ${event.configName} (ID: ${event.entryConfig.id})")

        val entryTable = registerEntry(event.entryConfig.id)

        databaseManager.executeTransaction {
            SchemaUtils.create(entryTable)
        }
        logger.info("已为 Entry ${event.entryConfig.id} 创建数据库表")
    }
}
