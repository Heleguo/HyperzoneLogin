package icu.h2l.login.auth.online.db

import com.velocitypowered.api.event.Subscribe
import `fun`.iiii.h2l.api.db.HyperZoneTransactionApi
import `fun`.iiii.h2l.api.db.table.ProfileTable
import `fun`.iiii.h2l.api.event.db.EntryTableSchemaAction
import `fun`.iiii.h2l.api.event.db.EntryTableSchemaEvent
import `fun`.iiii.h2l.api.event.db.EntryTableSchemaEventApi
import icu.h2l.login.auth.online.events.EntryRegisterEvent
import org.jetbrains.exposed.sql.SchemaUtils
import java.util.logging.Logger

/**
 * Entry 表管理器
 * 负责 Entry 表的注册、查询、创建、删除与事件处理
 */
class EntryTableManager(
    private val logger: Logger,
    private val tablePrefix: String,
    private val profileTable: ProfileTable
) {
    private val entryTables = mutableMapOf<String, EntryTable>()

    init {
        EntryTableSchemaEventApi.registerListener(::onSchemaEvent)
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
        HyperZoneTransactionApi.execute {
            entryTables.values.forEach { entryTable ->
                SchemaUtils.create(entryTable)
                logger.info("已创建表: ${entryTable.tableName}")
            }
        }
    }

    private fun dropAllEntryTables() {
        HyperZoneTransactionApi.execute {
            entryTables.values.forEach { entryTable ->
                SchemaUtils.drop(entryTable)
                logger.warning("已删除表: ${entryTable.tableName}")
            }
        }
    }

    private fun onSchemaEvent(event: EntryTableSchemaEvent) {
        when (event.action) {
            EntryTableSchemaAction.CREATE_ALL -> createAllEntryTables()
            EntryTableSchemaAction.DROP_ALL -> dropAllEntryTables()
        }
    }

    @Subscribe
    fun onEntryRegister(event: EntryRegisterEvent) {
        logger.info("接收到 Entry 注册事件: ${event.configName} (ID: ${event.entryConfig.id})")

        val entryTable = registerEntry(event.entryConfig.id)

        HyperZoneTransactionApi.execute {
            SchemaUtils.create(entryTable)
        }
        logger.info("已为 Entry ${event.entryConfig.id} 创建数据库表")
    }
}
