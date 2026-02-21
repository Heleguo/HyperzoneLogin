package icu.h2l.login.auth.offline.db

import com.velocitypowered.api.event.Subscribe
import icu.h2l.api.db.HyperZoneDatabaseManager
import icu.h2l.api.db.table.ProfileTable
import icu.h2l.api.event.db.TableSchemaAction
import icu.h2l.api.event.db.TableSchemaEvent
import icu.h2l.api.log.info
import icu.h2l.api.log.warn
import org.jetbrains.exposed.sql.SchemaUtils

class OfflineAuthTableManager(
    private val databaseManager: HyperZoneDatabaseManager,
    tablePrefix: String,
    profileTable: ProfileTable
) {
    val offlineAuthTable = OfflineAuthTable(tablePrefix, profileTable)

    fun createTable() {
        databaseManager.executeTransaction {
            SchemaUtils.create(offlineAuthTable)
        }
    }

    fun dropTable() {
        databaseManager.executeTransaction {
            SchemaUtils.drop(offlineAuthTable)
            warn { "已删除表: ${offlineAuthTable.tableName}" }
        }
    }

    @Subscribe
    fun onSchemaEvent(event: TableSchemaEvent) {
        when (event.action) {
            TableSchemaAction.CREATE_ALL -> createTable()
            TableSchemaAction.DROP_ALL -> dropTable()
        }
    }
}