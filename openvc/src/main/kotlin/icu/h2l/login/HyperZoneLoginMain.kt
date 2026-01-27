package icu.h2l.login

import com.google.inject.Inject
import com.google.inject.Injector
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import icu.h2l.login.command.HyperZoneLoginCommand
import icu.h2l.login.config.DatabaseSourceConfig
import icu.h2l.login.config.OfflineMatchConfig
import icu.h2l.login.database.DatabaseConfig
import icu.h2l.login.limbo.LimboAuth
import icu.h2l.login.manager.EntryConfigManager
import icu.h2l.login.listener.EventListener
import icu.h2l.login.manager.LoginServerManager
import java.nio.file.Files
import java.nio.file.Path
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.objectmapping.ObjectMapper

@Suppress("ANNOTATION_WILL_BE_APPLIED_ALSO_TO_PROPERTY_OR_FIELD")
class HyperZoneLoginMain @Inject constructor(
    private val server: ProxyServer,
    val logger: ComponentLogger,
    @DataDirectory private val dataDirectory: Path,
    private val injector: Injector
) {
    lateinit var loginServerManager: LoginServerManager
    lateinit var limboServerManager: LimboAuth
    lateinit var entryConfigManager: EntryConfigManager
    lateinit var databaseManager: icu.h2l.login.manager.DatabaseManager

    companion object {
        private lateinit var instance: HyperZoneLoginMain
        private lateinit var offlineMatchConfig: OfflineMatchConfig
        private lateinit var databaseSourceConfig: DatabaseSourceConfig

        @JvmStatic
        fun getInstance(): HyperZoneLoginMain = instance

        @JvmStatic
        fun getConfig(): OfflineMatchConfig = offlineMatchConfig
        
        @JvmStatic
        fun getDatabaseConfig(): DatabaseSourceConfig = databaseSourceConfig
    }

    init {
        instance = this
    }

    @Subscribe
    fun onEnable(event: ProxyInitializeEvent) {
        loadConfig()
        loadDatabaseConfig()
        connectDatabase()
        
        // 必须先注册 DatabaseManager 事件监听器，然后再加载 Entry 配置
        // 这样 EntryConfigManager 发布的 EntryRegisterEvent 才能被 DatabaseManager 接收
        proxy.eventManager.register(this, databaseManager)
        loadEntryConfigs()
        
        // Entry 加载完成后，创建基础表（Profile 表等）
        createBaseTables()

        loginServerManager = LoginServerManager()
        limboServerManager = LimboAuth(server)
        limboServerManager.load()

        proxy.commandManager.register("hzl", HyperZoneLoginCommand())
        proxy.eventManager.register(this, EventListener())
        proxy.eventManager.register(this, limboServerManager)

    }

    val proxy: ProxyServer
        get() = server

    private fun loadConfig() {
        val path = dataDirectory.resolve("offlinematch.conf")
        val firstCreation = Files.notExists(path)
        val loader = HoconConfigurationLoader.builder()
            .defaultOptions { opts: ConfigurationOptions ->
                opts
                    .shouldCopyDefaults(true)
                    .header(
                        """
                            HyperZoneLogin | by ksqeib
                            
                        """.trimIndent()
                    ).serializers { s ->
                        s.registerAnnotatedObjects(
                            ObjectMapper.factoryBuilder().addDiscoverer(dataClassFieldDiscoverer()).build()
                        )
                    }
            }
            .path(path)
            .build()
        val node = loader.load()
        val config = node.get(OfflineMatchConfig::class.java)
        if (firstCreation) {
            node.set(config)
            loader.save(node)
        }
        if (config != null) {
            offlineMatchConfig = config
        }
    }

    private fun loadEntryConfigs() {
        entryConfigManager = EntryConfigManager(dataDirectory, logger, proxy)
        entryConfigManager.loadAllConfigs()
    }
    
    private fun loadDatabaseConfig() {
        val path = dataDirectory.resolve("database.conf")
        val firstCreation = Files.notExists(path)
        val loader = HoconConfigurationLoader.builder()
            .defaultOptions { opts: ConfigurationOptions ->
                opts
                    .shouldCopyDefaults(true)
                    .header(
                        """
                            HyperZoneLogin Database Configuration | by ksqeib
                            
                        """.trimIndent()
                    ).serializers { s ->
                        s.registerAnnotatedObjects(
                            ObjectMapper.factoryBuilder().addDiscoverer(dataClassFieldDiscoverer()).build()
                        )
                    }
            }
            .path(path)
            .build()
        val node = loader.load()
        val config = node.get(DatabaseSourceConfig::class.java)
        if (firstCreation) {
            node.set(config)
            loader.save(node)
        }
        if (config != null) {
            databaseSourceConfig = config
        }
    }
    
    private fun connectDatabase() {
        logger.info("正在初始化数据库...")
        
        val dbConfig = when (databaseSourceConfig.type.uppercase()) {
            "SQLITE" -> {
                val dbPath = dataDirectory.resolve(databaseSourceConfig.sqlite.path)
                DatabaseConfig.sqlite(
                    path = dbPath.toString(),
                    tablePrefix = databaseSourceConfig.tablePrefix,
                    maximumPoolSize = databaseSourceConfig.pool.maximumPoolSize,
                    minimumIdle = databaseSourceConfig.pool.minimumIdle,
                    connectionTimeout = databaseSourceConfig.pool.connectionTimeout,
                    idleTimeout = databaseSourceConfig.pool.idleTimeout,
                    maxLifetime = databaseSourceConfig.pool.maxLifetime
                )
            }
            "MYSQL" -> {
                DatabaseConfig.mysql(
                    host = databaseSourceConfig.mysql.host,
                    port = databaseSourceConfig.mysql.port,
                    database = databaseSourceConfig.mysql.database,
                    username = databaseSourceConfig.mysql.username,
                    password = databaseSourceConfig.mysql.password,
                    tablePrefix = databaseSourceConfig.tablePrefix,
                    parameters = databaseSourceConfig.mysql.parameters,
                    maximumPoolSize = databaseSourceConfig.pool.maximumPoolSize,
                    minimumIdle = databaseSourceConfig.pool.minimumIdle,
                    connectionTimeout = databaseSourceConfig.pool.connectionTimeout,
                    idleTimeout = databaseSourceConfig.pool.idleTimeout,
                    maxLifetime = databaseSourceConfig.pool.maxLifetime
                )
            }
            "H2" -> {
                val dbPath = dataDirectory.resolve(databaseSourceConfig.h2.path)
                DatabaseConfig.h2(
                    path = dbPath.toString(),
                    tablePrefix = databaseSourceConfig.tablePrefix,
                    maximumPoolSize = databaseSourceConfig.pool.maximumPoolSize,
                    minimumIdle = databaseSourceConfig.pool.minimumIdle,
                    connectionTimeout = databaseSourceConfig.pool.connectionTimeout,
                    idleTimeout = databaseSourceConfig.pool.idleTimeout,
                    maxLifetime = databaseSourceConfig.pool.maxLifetime
                )
            }
            else -> {
                logger.error("不支持的数据库类型: ${databaseSourceConfig.type}, 使用默认 SQLite")
                val dbPath = dataDirectory.resolve(databaseSourceConfig.sqlite.path)
                DatabaseConfig.sqlite(
                    path = dbPath.toString(),
                    tablePrefix = databaseSourceConfig.tablePrefix
                )
            }
        }
        
        databaseManager = icu.h2l.login.manager.DatabaseManager(
            logger = java.util.logging.Logger.getLogger("HyperZoneLogin"),
            config = dbConfig
        )
        
        databaseManager.connect()
        
        logger.info("数据库连接完成")
    }
    
    private fun createBaseTables() {
        logger.info("正在创建基础数据表...")
        databaseManager.createBaseTables()
        logger.info("基础数据表创建完成")
    }
}

