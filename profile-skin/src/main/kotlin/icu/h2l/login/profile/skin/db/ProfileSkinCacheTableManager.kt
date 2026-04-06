package icu.h2l.login.profile.skin.db

import com.velocitypowered.api.event.Subscribe
import icu.h2l.api.db.HyperZoneDatabaseManager
import icu.h2l.api.event.db.TableSchemaAction
import icu.h2l.api.event.db.TableSchemaEvent
import icu.h2l.api.log.warn
import org.jetbrains.exposed.sql.SchemaUtils

class ProfileSkinCacheTableManager(
    private val databaseManager: HyperZoneDatabaseManager,
    val table: ProfileSkinCacheTable
) {
    fun createTable() {
        databaseManager.executeTransaction {
            SchemaUtils.create(table)
        }
    }

    fun dropTable() {
        databaseManager.executeTransaction {
            SchemaUtils.drop(table)
            warn { "已删除表: ${table.tableName}" }
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

