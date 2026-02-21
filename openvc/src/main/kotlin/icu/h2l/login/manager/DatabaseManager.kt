package icu.h2l.login.manager

import com.velocitypowered.api.proxy.ProxyServer
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import icu.h2l.api.db.HyperZoneDatabaseManager
import icu.h2l.api.db.table.ProfileTable
import icu.h2l.api.event.db.TableSchemaAction
import icu.h2l.api.event.db.TableSchemaEvent
import icu.h2l.api.log.info
import icu.h2l.api.log.warn
import icu.h2l.login.database.DatabaseConfig
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.Thread.currentThread

/**
 * 数据库管理类
 * 负责数据库连接和表的创建
 */
class DatabaseManager(
    private val config: DatabaseConfig,
    private val proxy: ProxyServer
) : HyperZoneDatabaseManager {
    private lateinit var database: Database
    private lateinit var dataSource: HikariDataSource
    
    /**
     * 档案表实例
     */
    private val profileTable = ProfileTable(config.tablePrefix)

    override val tablePrefix: String
        get() = config.tablePrefix

    /**
     * 连接数据库
     */
    fun connect() {
        info { "正在连接数据库..." }

        val pluginClassLoader = this::class.java.classLoader
        try {
            Class.forName(config.driverClassName, true, pluginClassLoader)
        } catch (ex: ClassNotFoundException) {
            throw RuntimeException("无法加载数据库驱动: ${config.driverClassName}", ex)
        }
        
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

        val thread = currentThread()
        val previousContextClassLoader = thread.contextClassLoader
        thread.contextClassLoader = pluginClassLoader
        try {
            dataSource = HikariDataSource(hikariConfig)
        } finally {
            thread.contextClassLoader = previousContextClassLoader
        }
        database = Database.connect(dataSource)
        
        info { "数据库连接成功！" }
    }
    
    /**
     * 断开数据库连接
     */
    fun disconnect() {
        if (::dataSource.isInitialized && !dataSource.isClosed) {
            info { "正在断开数据库连接..." }
            dataSource.close()
            info { "数据库连接已断开！" }
        }
    }

    
    /**
     * 获取档案表实例
     */
    fun getProfileTable(): ProfileTable = profileTable
    
    /**
     * 创建基础表（不包括 Entry 表）
     * Entry 表由事件系统自动创建
     */
    fun createBaseTables() {
        transaction(database) {
            // 创建档案表
            SchemaUtils.create(profileTable)
        }
    }
    
    /**
     * 删除所有表（谨慎使用）
     */
    fun dropTables() {
        warn { "正在删除数据库表..." }

        // 通知模块删除所有入口表
        proxy.eventManager.fire(TableSchemaEvent(TableSchemaAction.DROP_ALL)).join()

        executeTransaction {
            // 删除档案表
            SchemaUtils.drop(profileTable)
            warn { "已删除表: ${profileTable.tableName}" }
        }

        warn { "数据库表已全部删除！" }
    }

    /**
     * 执行数据库事务
     */
    override fun <T> executeTransaction(statement: () -> T): T {
        return transaction(database) {
            statement()
        }
    }
}
