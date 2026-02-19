package icu.h2l.login.manager

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import `fun`.iiii.h2l.api.db.HyperZoneTransactionApi
import `fun`.iiii.h2l.api.db.HyperZoneTransactionExecutor
import `fun`.iiii.h2l.api.db.table.ProfileTable
import `fun`.iiii.h2l.api.event.db.EntryTableSchemaAction
import `fun`.iiii.h2l.api.event.db.EntryTableSchemaEvent
import `fun`.iiii.h2l.api.event.db.EntryTableSchemaEventApi
import icu.h2l.login.database.DatabaseConfig
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.logging.Logger

/**
 * 数据库管理类
 * 负责数据库连接和表的创建
 */
class DatabaseManager(
    private val logger: Logger,
    private val config: DatabaseConfig
) {
    private lateinit var database: Database
    private lateinit var dataSource: HikariDataSource
    
    /**
     * 档案表实例
     */
    private val profileTable = ProfileTable(config.tablePrefix)

    /**
     * 连接数据库
     */
    fun connect() {
        logger.info("正在连接数据库...")
        
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.username
            password = config.password
            driverClassName = config.driverClassName
            
            // 连接池配置
            maximumPoolSize = config.maximumPoolSize
            minimumIdle = config.minimumIdle
            connectionTimeout = config.connectionTimeout
            idleTimeout = config.idleTimeout
            maxLifetime = config.maxLifetime
            
            // 连接测试
            connectionTestQuery = "SELECT 1"
            
            // 其他配置
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        }
        
        dataSource = HikariDataSource(hikariConfig)
        database = Database.connect(dataSource)

        HyperZoneTransactionApi.registerExecutor(object : HyperZoneTransactionExecutor {
            override fun <T> execute(statement: () -> T): T {
                return transaction(database) {
                    statement()
                }
            }
        })
        
        logger.info("数据库连接成功！")
    }
    
    /**
     * 断开数据库连接
     */
    fun disconnect() {
        if (::dataSource.isInitialized && !dataSource.isClosed) {
            logger.info("正在断开数据库连接...")
            dataSource.close()
            logger.info("数据库连接已断开！")
        }
    }

    
    /**
     * 获取档案表实例
     */
    fun getProfileTable(): ProfileTable = profileTable
    
    /**
     * 创建所有表
     */
    fun createTables() {
        executeTransaction {
            logger.info("正在创建数据库表...")
            
            // 创建档案表
            SchemaUtils.create(profileTable)
            logger.info("已创建表: ${profileTable.tableName}")
        }

        // 通知模块创建所有入口表
        EntryTableSchemaEventApi.fire(EntryTableSchemaEvent(EntryTableSchemaAction.CREATE_ALL))

        logger.info("数据库表创建完成！")
    }
    
    /**
     * 创建基础表（不包括 Entry 表）
     * Entry 表由事件系统自动创建
     */
    fun createBaseTables() {
        transaction(database) {
            // 创建档案表
            SchemaUtils.create(profileTable)
            logger.info("已创建表: ${profileTable.tableName}")
        }
    }
    
    /**
     * 删除所有表（谨慎使用）
     */
    fun dropTables() {
        logger.warning("正在删除数据库表...")

        // 通知模块删除所有入口表
        EntryTableSchemaEventApi.fire(EntryTableSchemaEvent(EntryTableSchemaAction.DROP_ALL))

        executeTransaction {
            // 删除档案表
            SchemaUtils.drop(profileTable)
            logger.warning("已删除表: ${profileTable.tableName}")
        }

        logger.warning("数据库表已全部删除！")
    }

    /**
     * 执行数据库事务
     */
    fun <T> executeTransaction(statement: () -> T): T {
        return transaction(database) {
            statement()
        }
    }
}
