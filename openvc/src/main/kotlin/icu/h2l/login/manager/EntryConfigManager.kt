package icu.h2l.login.manager

import com.velocitypowered.api.proxy.ProxyServer
import icu.h2l.login.api.EntryRegisterEvent
import icu.h2l.login.config.entry.EntryConfig
import icu.h2l.login.util.debug
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.objectmapping.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * EntryConfig 管理器，负责从 entry 文件夹中加载所有配置文件
 */
class EntryConfigManager(
    private val dataDirectory: Path,
    private val logger: ComponentLogger,
    private val proxyServer: ProxyServer
) {
    private val entryConfigs = mutableMapOf<String, EntryConfig>()

    companion object {
        private const val ENTRY_FOLDER = "entry"
        private const val EXAMPLE_FOLDER = "example"
        private const val CONFIG_EXTENSION = ".conf"
    }

    /**
     * 加载所有 entry 配置文件
     */
    fun loadAllConfigs() {
        val entryDir = dataDirectory.resolve(ENTRY_FOLDER)

        // 如果目录不存在，创建目录并生成示例配置
        if (Files.notExists(entryDir)) {
            Files.createDirectories(entryDir)
            createExampleConfig(entryDir)
            debug { "创建 entry 目录和示例配置文件" }
            return
        }

        // 扫描并加载所有配置文件
        scanAndLoadConfigs(entryDir)

        logger.info("成功加载 ${entryConfigs.size} 个 Entry 配置文件")
    }

    /**
     * 扫描目录并加载配置文件
     */
    private fun scanAndLoadConfigs(directory: Path) {
        if (!directory.isDirectory()) return

        directory.listDirectoryEntries().forEach { path ->
            when {
                // 跳过 example 文件夹
                path.isDirectory() && path.name.equals(EXAMPLE_FOLDER, ignoreCase = true) -> {
                    debug { "跳过 example 文件夹: ${path.name}" }
                }
                // 递归扫描子目录
                path.isDirectory() -> {
                    scanAndLoadConfigs(path)
                }
                // 加载 conf 配置文件
                path.name.endsWith(CONFIG_EXTENSION, ignoreCase = true) -> {
                    loadConfig(path)
                }
            }
        }
    }

    /**
     * 加载单个配置文件
     */
    private fun loadConfig(path: Path) {
        try {
            val loader = HoconConfigurationLoader.builder()
                .defaultOptions { opts: ConfigurationOptions ->
                    opts
                        .shouldCopyDefaults(true)
                        .header(
                            """
                            HyperZoneLogin Entry Configuration
                            配置文件格式为 HOCON
                            
                            """.trimIndent()
                        )
                        .serializers { s ->
                            s.registerAnnotatedObjects(
                                ObjectMapper.factoryBuilder()
                                    .addDiscoverer(dataClassFieldDiscoverer())
                                    .build()
                            )
                        }
                }
                .path(path)
                .build()

            val node = loader.load()
            val config = node.get(EntryConfig::class.java)

            if (config != null) {
                // 验证配置有效性
                // 检查 ID 是否重复
                if (entryConfigs.values.any { it.id == config.id }) {
                    logger.error("配置文件 ${path.fileName} 的 ID ${config.id} 与其他配置重复，跳过加载")
                    return
                }

                val configName = path.fileName.toString().removeSuffix(CONFIG_EXTENSION)
                entryConfigs[configName] = config
                debug { "成功加载配置: $configName (ID: ${config.id}, Name: ${config.name})" }
                
                // 发布 Entry 注册事件
                proxyServer.eventManager.fireAndForget(EntryRegisterEvent(configName, config))
            } else {
                logger.error("无法解析配置文件: ${path.fileName}")
            }
        } catch (e: Exception) {
            logger.error("加载配置文件 ${path.fileName} 时出错: ${e.message}", e)
        }
    }

    /**
     * 创建示例配置文件
     */
    private fun createExampleConfig(entryDir: Path) {
        val exampleDir = entryDir.resolve(EXAMPLE_FOLDER)
        Files.createDirectories(exampleDir)

        val examplePath = exampleDir.resolve("example$CONFIG_EXTENSION")
        val loader = HoconConfigurationLoader.builder()
            .defaultOptions { opts: ConfigurationOptions ->
                opts
                    .shouldCopyDefaults(true)
                    .header(
                        """
                        HyperZoneLogin Entry Configuration - Example
                        这是一个示例配置文件，位于 example 文件夹中的配置不会被加载
                        复制此文件到 entry 文件夹（非 example 子文件夹）中并修改即可使用
                        
                        """.trimIndent()
                    )
                    .serializers { s ->
                        s.registerAnnotatedObjects(
                            ObjectMapper.factoryBuilder()
                                .addDiscoverer(dataClassFieldDiscoverer())
                                .build()
                        )
                    }
            }
            .path(examplePath)
            .build()

        val node = loader.createNode()
        val exampleConfig = EntryConfig()
        node.set(EntryConfig::class.java, exampleConfig)
        loader.save(node)

        debug { "创建示例配置文件: ${examplePath.fileName}" }
    }

    /**
     * 获取所有加载的配置
     */
    fun getAllConfigs(): Map<String, EntryConfig> = entryConfigs.toMap()

    /**
     * 根据配置名称获取配置
     */
    fun getConfig(name: String): EntryConfig? = entryConfigs[name]

    /**
     * 根据 ID 获取配置
     */
    fun getConfigById(id: String): EntryConfig? = entryConfigs.values.find { it.id == id }

    /**
     * 重新加载所有配置
     */
    fun reloadConfigs() {
        entryConfigs.clear()
        loadAllConfigs()
    }
}
