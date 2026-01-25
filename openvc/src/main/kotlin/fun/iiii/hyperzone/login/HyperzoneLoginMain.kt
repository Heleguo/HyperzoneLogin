package `fun`.iiii.hyperzone.login

import com.google.inject.Inject
import com.google.inject.Injector
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import `fun`.iiii.hyperzone.LoginServerManager
import `fun`.iiii.hyperzone.login.command.HyperZoneLoginCommand
import `fun`.iiii.hyperzone.login.config.HyperZoneLoginConfig
import `fun`.iiii.hyperzone.login.limbo.LimboAuth
import `fun`.iiii.hyperzone.login.listener.EventListener
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.objectmapping.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier

class HyperzoneLoginMain @Inject constructor(
    private val server: ProxyServer,
    val logger: ComponentLogger,
    @DataDirectory private val dataDirectory: Path,
    private val injector: Injector
) {
    lateinit var loginServerManager: LoginServerManager
    lateinit var limboServerManager: LimboAuth

    companion object {
        private lateinit var instance: HyperzoneLoginMain
        private lateinit var hyperZoneLoginConfig: HyperZoneLoginConfig

        @JvmStatic
        fun getInstance(): HyperzoneLoginMain = instance

        @JvmStatic
        fun getConfig(): HyperZoneLoginConfig = hyperZoneLoginConfig
    }

    init {
        instance = this
    }

    @Subscribe
    fun onEnable(event: ProxyInitializeEvent) {
        loadConfig()
        loginServerManager = LoginServerManager()
        limboServerManager = LimboAuth(server)

        proxy.commandManager.register("hzl", HyperZoneLoginCommand())
        proxy.eventManager.register(this, EventListener())
        proxy.eventManager.register(this, limboServerManager)
        limboServerManager.load()

    }

    val proxy: ProxyServer
        get() = server

    private fun loadConfig() {
        val path = dataDirectory.resolve("config.conf")
        val firstCreation = Files.notExists(path)
        val loader = HoconConfigurationLoader.builder()
            .defaultOptions { opts: ConfigurationOptions ->
                opts
                    .shouldCopyDefaults(true)
                    .header(
                        """
                            HyperzoneLogin | by ksqeib
                            
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
        val config = node.get(HyperZoneLoginConfig::class.java)
        if (firstCreation) {
            node.set(config)
            loader.save(node)
        }
        if (config != null) {
            hyperZoneLoginConfig = config
        }
    }


    fun logInfo(msg: String) {
        logger.info(msg)
    }

    fun logDebug(msg: String) {
        if (hyperZoneLoginConfig.advanced.debug) {
            logger.info("[DEBUG] $msg")
        }
    }

    fun logDebug(msg: Supplier<String>) {
        if (hyperZoneLoginConfig.advanced.debug) {
            logger.info("[DEBUG] ${msg.get()}")
        }
    }

} 