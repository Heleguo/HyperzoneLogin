/*
 * This file is part of HyperZoneLogin, licensed under the GNU Affero General Public License v3.0 or later.
 *
 * Copyright (C) ksqeib (庆灵) <ksqeib@qq.com>
 * Copyright (C) contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.util.Locale

plugins {
    java
}

dependencies {
    runtimeOnly(libs.velocityProxy)
    runtimeOnly(libs.velocityApi)
}

val runDir = layout.projectDirectory.dir("run")
val velocityToml = runDir.file("velocity.toml")
val runtimeDependencyProjects = listOf(
    project(":velocity"),
    project(":auth-offline"),
    project(":data-merge"),
)

fun cacheFileName(groupId: String, artifactId: String, version: String): String =
    (groupId + "-" + artifactId + "-" + version + ".jar")
        .lowercase(Locale.ROOT)
        .replace(':', '-')
        .replace('.', '-')

val prepareVelocityRun = tasks.register<Sync>("prepareVelocityRun") {
    group = "application"
    description = "Stages a local Velocity proxy run directory with the HyperZoneLogin monolith plugin."

    dependsOn(rootProject.tasks.named("collectPluginJars"))

    into(runDir)
    from(rootProject.layout.buildDirectory.dir("HZL")) {
        into("plugins")
    }
//localhost:25575
    doLast {
        val runDirFile = runDir.asFile
        runDirFile.mkdirs()
        val libsDir = runDirFile.resolve("plugins/hyperzonelogin/libs")
        libsDir.mkdirs()

        runtimeDependencyProjects
            .mapNotNull { it.configurations.findByName("needPackageResolver") }
            .forEach { configuration ->
                configuration.resolvedConfiguration.resolvedArtifacts
                    .filter { artifact ->
                        artifact.extension == "jar" && artifact.moduleVersion.id.group != "unspecified"
                    }
                    .sortedBy { artifact ->
                        val moduleId = artifact.moduleVersion.id
                        moduleId.group + ":" + artifact.name + ":" + moduleId.version
                    }
                    .forEach { artifact ->
                        val moduleId = artifact.moduleVersion.id
                        val targetFile = libsDir.resolve(cacheFileName(moduleId.group, artifact.name, moduleId.version))
                        artifact.file.copyTo(targetFile, overwrite = true)
                    }
            }

        velocityToml.asFile.writeText(
            """
            config-version = "2.8"
            bind = "127.0.0.1:25575"
            motd = "<green>HyperZoneLogin VC Runtest"
            show-max-players = 1
            online-mode = false
            force-key-authentication = false
            prevent-client-proxy-connections = false
            player-info-forwarding-mode = "modern"
            forwarding-secret-file = "forwarding.secret"
            announce-forge = false
            kick-existing-players = false
            ping-passthrough = "DISABLED"
            sample-players-in-ping = false
            enable-player-address-logging = false

            [packet-limiter]
            interval = 7
            packets-per-second = -1
            bytes-per-second = -1
            decompressed-bytes-per-second = 5242880

            [servers]
            play = "127.0.0.1:30067"
            outpre-auth = "127.0.0.1:30066"
            try = [
                "play"
            ]

            [forced-hosts]

            [advanced]
            compression-threshold = 256
            compression-level = -1
            login-ratelimit = 0
            connection-timeout = 5000
            read-timeout = 30000
            haproxy-protocol = false
            tcp-fast-open = false
            bungee-plugin-message-channel = true
            show-ping-requests = false
            failover-on-unexpected-server-disconnect = true
            announce-proxy-commands = true
            log-command-executions = false
            log-player-connections = true
            accepts-transfers = false
            enable-reuse-port = false
            command-rate-limit = 50
            forward-commands-if-rate-limited = true
            kick-after-rate-limited-commands = 0
            tab-complete-rate-limit = 10
            kick-after-rate-limited-tab-completes = 0

            [query]
            enabled = false
            port = 25565
            map = "Velocity"
            show-plugins = false
            """.trimIndent() + "\n"
        )

        val bStatsConfig = runDirFile.resolve("plugins/bStats/config.txt")
        bStatsConfig.parentFile.mkdirs()
        bStatsConfig.writeText(
            """
                # bStats (https://bStats.org) collects some basic information for plugin authors, like
                # how many people use their plugin and their total player count. It's recommended to keep
                # bStats enabled, but if you're not comfortable with this, you can turn this setting off.
                # There is no performance penalty associated with having metrics enabled, and data sent to
                # bStats is fully anonymous.
                # Learn more here: https://bstats.org/docs/server-owners
                enabled=false
                server-uuid=a8c7c030-3822-47d9-a9fc-124c59009ca8
                log-errors=false
                log-sent-data=false
                log-response-status-text=false
            """.trimIndent() + "\n"
        )

        runDirFile.resolve("forwarding.secret").writeText("xQHleQQvdFNe\n")

        val startConf = runDirFile.resolve("plugins/hyperzonelogin/start.conf")
        startConf.parentFile.mkdirs()
        startConf.writeText(
            """
            # HyperZoneLogin — 启动前置配置 / Startup Pre-configuration
            # 
            # 此文件是 HyperZoneLogin 第一个被读取的配置文件，不受 i18n 系统影响。
            # This file is the first configuration loaded by HyperZoneLogin and is NOT affected by the i18n system.
            # 
            # 请在完成所有设置后将 ready 设为 true，否则插件将拒绝启动。
            # Set `ready = true` after finishing all settings, or the plugin will refuse to start.
            # 
            # --- 字段说明 / Field Description ---
            # 
            #  language : 配置注释语言，影响其他配置文件首次生成时的注释语言。
            #             Locale key for config comment translation (e.g. zh_cn, en_us).
            # 
            #  format   : 配置文件格式，影响其他配置文件的序列化方式。
            #             Config serialization format: hocon | gson | yaml
            #             （当前版本仅完整支持 hocon，gson/yaml 为预留选项）
            #             (Current version fully supports hocon only; gson/yaml are reserved for future use)
            # 
            #  ready    : 就绪标志，必须为 true 插件才会正常启动。
            #             Must be true for the plugin to start normally.
            # 
            # --- 文档 / Documentation ---
            # 
            #  用户文档站  : https://docs.h2l.icu
            #  GitHub     : https://github.com/HyperZoneLogin/HyperzoneLogin
            #  Issues     : https://github.com/HyperZoneLogin/HyperzoneLogin/issues
            # 
            # --- 社区 / Community ---
            # 
            #  Discord    : https://discord.gg/dCAeNyR9TA
            #  QQ 群      : https://qm.qq.com/q/GZWVfEyokS
            # 
            # --- 支持项目 / Support the Project ---
            # 
            #  如果 HyperZoneLogin 对你有帮助，欢迎前往 GitHub 点一个 Star ⭐，这对项目非常重要！
            #  If HyperZoneLogin has been helpful to you, please consider giving it a Star ⭐ on GitHub!
            #  → https://github.com/HyperZoneLogin/HyperzoneLogin
            
            language="zh_cn"
            format=hocon
            ready=true
            """.trimIndent() + "\n"
        )
    }
}

tasks.register<JavaExec>("runVelocity") {
    group = "application"
    description = "Runs a local Velocity proxy with HyperZoneLogin plugins staged in run/plugins."

    dependsOn(prepareVelocityRun)

    classpath = sourceSets.named("main").get().runtimeClasspath
    mainClass.set("com.velocitypowered.proxy.Velocity")
    workingDir = runDir.asFile
    standardInput = System.`in`
    jvmArgs(
        "-Dfile.encoding=UTF-8",
        "-Dsun.stdout.encoding=UTF-8",
        "-Dsun.stderr.encoding=UTF-8",
        "--enable-native-access=ALL-UNNAMED",
    )
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
}
