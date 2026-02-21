package icu.h2l.login.auth.online.db

import com.velocitypowered.api.event.Subscribe
import icu.h2l.api.db.HyperZoneDatabaseManager
import icu.h2l.api.db.table.ProfileTable
import icu.h2l.api.event.db.TableSchemaAction
import icu.h2l.api.event.db.TableSchemaEvent
import icu.h2l.login.auth.online.events.EntryRegisterEvent
import icu.h2l.api.log.info
import icu.h2l.api.log.warn
import org.jetbrains.exposed.sql.SchemaUtils

/**
 * Entry 表管理器
 * 负责 Entry 表的注册、查询、创建、删除与事件处理
 */
class EntryTableManager(
    private val databaseManager: HyperZoneDatabaseManager,
    private val tablePrefix: String,
    private val profileTable: ProfileTable
) {
    private val entryTables = mutableMapOf<String, EntryTable>()

    fun registerEntry(entryId: String): EntryTable {
        val normalizedId = entryId.lowercase()
        return entryTables.getOrPut(normalizedId) {
            info { "注册入口表: $normalizedId" }
            EntryTable(normalizedId, tablePrefix, profileTable)
        }
    }

    fun getEntryTable(entryId: String): EntryTable? {
        return entryTables[entryId.lowercase()]
    }

    fun createAllEntryTables() {
        databaseManager.executeTransaction {
            entryTables.values.forEach { entryTable ->
                SchemaUtils.create(entryTable)
            }
        }
    }

   fun dropAllEntryTables() {
        databaseManager.executeTransaction {
            entryTables.values.forEach { entryTable ->
                SchemaUtils.drop(entryTable)
                warn { "已删除表: ${entryTable.tableName}" }
            }
        }
    }

    @Subscribe
    fun onSchemaEvent(event: TableSchemaEvent) {
        when (event.action) {
            TableSchemaAction.CREATE_ALL -> createAllEntryTables()
            TableSchemaAction.DROP_ALL -> dropAllEntryTables()
        }
    }

    @Subscribe
    fun onEntryRegister(event: EntryRegisterEvent) {
        info { "接收到 Entry 注册事件: ${event.configName} (ID: ${event.entryConfig.id})" }

        val entryTable = registerEntry(event.entryConfig.id)

        databaseManager.executeTransaction {
            SchemaUtils.create(entryTable)
        }
    }
}
