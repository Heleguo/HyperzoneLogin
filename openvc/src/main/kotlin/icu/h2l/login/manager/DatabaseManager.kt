package icu.h2l.login.manager

import com.velocitypowered.api.event.Subscribe
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import icu.h2l.login.api.EntryRegisterEvent
import icu.h2l.login.database.DatabaseConfig
import icu.h2l.login.database.tables.EntryTable
import icu.h2l.login.database.tables.ProfileTable
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
     * 存储所有已注册的入口表
     */
    private val entryTables = mutableMapOf<String, EntryTable>()
    
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
     * 注册入口表
     * 
     * @param entryId 入口ID，如 "mojang"、"offline"
     * @return 创建的入口表实例
     */
    fun registerEntry(entryId: String): EntryTable {
        val normalizedId = entryId.lowercase()
        
        return entryTables.getOrPut(normalizedId) {
            logger.info("注册入口表: $normalizedId")
            EntryTable(normalizedId, config.tablePrefix, profileTable)
        }
    }
    
    /**
     * 获取已注册的入口表
     * 
     * @param entryId 入口ID
     * @return 入口表实例，如果未注册则返回 null
     */
    fun getEntryTable(entryId: String): EntryTable? {
        return entryTables[entryId.lowercase()]
    }
    
    /**
     * 获取档案表实例
     */
    fun getProfileTable(): ProfileTable = profileTable
    
    /**
     * 创建所有表
     */
    fun createTables() {
        transaction(database) {
            logger.info("正在创建数据库表...")
            
            // 创建档案表
            SchemaUtils.create(profileTable)
            logger.info("已创建表: ${profileTable.tableName}")
            
            // 创建所有入口表
            entryTables.values.forEach { entryTable ->
                SchemaUtils.create(entryTable)
                logger.info("已创建表: ${entryTable.tableName}")
            }
            
            logger.info("数据库表创建完成！")
        }
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
        transaction(database) {
            logger.warning("正在删除数据库表...")
            
            // 删除所有入口表
            entryTables.values.forEach { entryTable ->
                SchemaUtils.drop(entryTable)
                logger.warning("已删除表: ${entryTable.tableName}")
            }
            
            // 删除档案表
            SchemaUtils.drop(profileTable)
            logger.warning("已删除表: ${profileTable.tableName}")
            
            logger.warning("数据库表已全部删除！")
        }
    }
    
    /**
     * 执行数据库事务
     */
    fun <T> executeTransaction(statement: () -> T): T {
        return transaction(database) {
            statement()
        }
    }
    
    /**
     * 处理 Entry 注册事件
     * 当 EntryConfigManager 加载配置时自动注册对应的数据库表
     */
    @Subscribe
    fun onEntryRegister(event: EntryRegisterEvent) {
        logger.info("接收到 Entry 注册事件: ${event.configName} (ID: ${event.entryConfig.id})")
        
        // 注册 Entry 表
        val entryTable = registerEntry(event.entryConfig.id)
        
        // 如果数据库已连接，立即创建表
        if (::database.isInitialized) {
            transaction(database) {
                SchemaUtils.create(entryTable)
            }
            logger.info("已为 Entry ${event.entryConfig.id} 创建数据库表")
        }
    }
}
