package icu.h2l.login.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
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
}

/**
 * 数据库配置
 */
data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val driverClassName: String = "com.mysql.cj.jdbc.Driver",
    val tablePrefix: String = "",
    val maximumPoolSize: Int = 10,
    val minimumIdle: Int = 2,
    val connectionTimeout: Long = 30000,
    val idleTimeout: Long = 600000,
    val maxLifetime: Long = 1800000
) {
    companion object {
        /**
         * 创建 MySQL 配置
         */
        fun mysql(
            host: String,
            port: Int = 3306,
            database: String,
            username: String,
            password: String,
            tablePrefix: String = ""
        ) = DatabaseConfig(
            jdbcUrl = "jdbc:mysql://$host:$port/$database?useSSL=false&serverTimezone=UTC&characterEncoding=utf8",
            username = username,
            password = password,
            driverClassName = "com.mysql.cj.jdbc.Driver",
            tablePrefix = tablePrefix
        )
        
        /**
         * 创建 H2 配置（用于测试）
         */
        fun h2(
            path: String = "./data/hyperzone_login",
            tablePrefix: String = ""
        ) = DatabaseConfig(
            jdbcUrl = "jdbc:h2:file:$path;MODE=MySQL",
            username = "sa",
            password = "",
            driverClassName = "org.h2.Driver",
            tablePrefix = tablePrefix
        )
        
        /**
         * 创建 SQLite 配置（推荐用于单机部署）
         */
        fun sqlite(
            path: String = "./data/hyperzone_login.db",
            tablePrefix: String = ""
        ) = DatabaseConfig(
            jdbcUrl = "jdbc:sqlite:$path",
            username = "",
            password = "",
            driverClassName = "org.sqlite.JDBC",
            tablePrefix = tablePrefix
        )
    }
}
