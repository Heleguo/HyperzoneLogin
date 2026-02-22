package icu.h2l.login.merge

import com.velocitypowered.api.proxy.ProxyServer
import icu.h2l.api.db.HyperZoneDatabaseManager
import icu.h2l.api.log.info
import icu.h2l.api.log.warn
import icu.h2l.api.module.HyperSubModule
import icu.h2l.login.merge.command.MergeCommand
import icu.h2l.login.merge.config.MergeMlConfig
import icu.h2l.login.merge.service.MlDataMigrator
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.objectmapping.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path

class MergeSubModule : HyperSubModule {
    override fun register(
        owner: Any,
        proxy: ProxyServer,
        dataDirectory: Path,
        databaseManager: HyperZoneDatabaseManager
    ) {
        val mergeConfig = loadMergeConfig(dataDirectory)
        val migrator = MlDataMigrator(dataDirectory, databaseManager, mergeConfig)

        proxy.commandManager.register(
            "hzl-merge",
            MergeCommand {
                val report = migrator.migrate()
                "profiles(created=${report.targetProfilesCreated}, matched=${report.targetProfilesMatched}, failed=${report.targetProfileFailures}), " +
                    "entries(created=${report.targetEntriesCreated}, matched=${report.targetEntriesMatched}, conflicts=${report.targetEntryConflicts}, failed=${report.targetEntryFailures}, missingProfile=${report.missingProfileReference})"
            }
        )

        info { "MergeSubModule 已加载，命令 /hzl-merge ml 可用" }
    }

    private fun loadMergeConfig(dataDirectory: Path): MergeMlConfig {
        val mergeDirectory = dataDirectory.resolve("merge")
        Files.createDirectories(mergeDirectory)
        val path = mergeDirectory.resolve("merge-ml.conf")
        val firstCreation = Files.notExists(path)
        val loader = HoconConfigurationLoader.builder()
            .defaultOptions { opts: ConfigurationOptions ->
                opts
                    .shouldCopyDefaults(true)
                    .header(
                        """
                            HyperZoneLogin ML Merge Configuration
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
        val config = node.get(MergeMlConfig::class.java) ?: MergeMlConfig()

        if (firstCreation) {
            node.set(config)
            loader.save(node)
            warn { "首次创建 merge-ml.conf，请按需修改后再执行 /hzl-merge ml" }
        }

        return config
    }
}
