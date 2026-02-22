package icu.h2l.login.merge

import com.velocitypowered.api.proxy.ProxyServer
import icu.h2l.api.db.HyperZoneDatabaseManager
import icu.h2l.api.log.info
import icu.h2l.api.log.warn
import icu.h2l.api.module.HyperSubModule
import icu.h2l.login.merge.command.MergeCommand
import icu.h2l.login.merge.config.MergeAmConfig
import icu.h2l.login.merge.config.MergeMlConfig
import icu.h2l.login.merge.service.AmDataMigrator
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
        val mergeMlConfig = loadMergeMlConfig(dataDirectory)
        val mergeAmConfig = loadMergeAmConfig(dataDirectory)
        val mlMigrator = MlDataMigrator(dataDirectory, databaseManager, mergeMlConfig)
        val amMigrator = AmDataMigrator(dataDirectory, databaseManager, mergeAmConfig)

        proxy.commandManager.register(
            "hzl-merge",
            MergeCommand(
                runMlMigration = {
                    val report = mlMigrator.migrate()
                    "profiles(created=${report.targetProfilesCreated}, matched=${report.targetProfilesMatched}, failed=${report.targetProfileFailures}), " +
                        "entries(created=${report.targetEntriesCreated}, matched=${report.targetEntriesMatched}, conflicts=${report.targetEntryConflicts}, failed=${report.targetEntryFailures}, missingProfile=${report.missingProfileReference})"
                },
                runAmMigration = {
                    val report = amMigrator.migrate()
                    "profiles(created=${report.targetProfilesCreated}, matched=${report.targetProfilesMatched}, failed=${report.targetProfileFailures}), " +
                        "offlineAuth(created=${report.targetOfflineAuthCreated}, matched=${report.targetOfflineAuthMatched}, updated=${report.targetOfflineAuthUpdated}, conflicts=${report.targetOfflineAuthConflicts}, failed=${report.targetOfflineAuthFailures}, invalidPassword=${report.invalidPasswordFormat})"
                }
            )
        )

        info { "MergeSubModule 已加载，命令 /hzl-merge ml 和 /hzl-merge am 可用" }
    }

    private fun loadMergeMlConfig(dataDirectory: Path): MergeMlConfig {
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

    private fun loadMergeAmConfig(dataDirectory: Path): MergeAmConfig {
        val mergeDirectory = dataDirectory.resolve("merge")
        Files.createDirectories(mergeDirectory)
        val path = mergeDirectory.resolve("merge-am.conf")
        val firstCreation = Files.notExists(path)
        val loader = HoconConfigurationLoader.builder()
            .defaultOptions { opts: ConfigurationOptions ->
                opts
                    .shouldCopyDefaults(true)
                    .header(
                        """
                            HyperZoneLogin AUTHME Merge Configuration
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
        val config = node.get(MergeAmConfig::class.java) ?: MergeAmConfig()

        if (firstCreation) {
            node.set(config)
            loader.save(node)
            warn { "首次创建 merge-am.conf，请按需修改后再执行 /hzl-merge am" }
        }

        return config
    }
}
