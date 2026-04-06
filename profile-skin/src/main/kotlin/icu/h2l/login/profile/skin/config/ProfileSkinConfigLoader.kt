package icu.h2l.login.profile.skin.config

import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.objectmapping.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path

object ProfileSkinConfigLoader {
    private const val FILE_NAME = "profile-skin.conf"

    fun load(dataDirectory: Path): ProfileSkinConfig {
        val path = dataDirectory.resolve(FILE_NAME)
        val firstCreation = Files.notExists(path)
        val loader = HoconConfigurationLoader.builder()
            .defaultOptions { opts: ConfigurationOptions ->
                opts
                    .shouldCopyDefaults(true)
                    .header(
                        """
                            HyperZoneLogin Profile Skin Configuration
                            配置文件格式为 HOCON
                        """.trimIndent()
                    )
                    .serializers { s ->
                        s.registerAnnotatedObjects(
                            ObjectMapper.factoryBuilder().addDiscoverer(dataClassFieldDiscoverer()).build()
                        )
                    }
            }
            .path(path)
            .build()

        val node = loader.load()
        val config = node.get(ProfileSkinConfig::class.java) ?: ProfileSkinConfig()
        if (firstCreation) {
            node.set(ProfileSkinConfig::class.java, config)
            loader.save(node)
        }
        return config
    }
}

