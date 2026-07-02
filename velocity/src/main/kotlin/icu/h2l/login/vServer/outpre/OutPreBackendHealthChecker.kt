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

package icu.h2l.login.vServer.outpre

import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.PingOptions
import com.velocitypowered.api.proxy.server.ServerInfo
import com.velocitypowered.api.proxy.server.ServerPing
import com.velocitypowered.api.scheduler.ScheduledTask
import com.velocitypowered.proxy.VelocityServer
import icu.h2l.api.log.HyperZoneDebugType
import icu.h2l.api.log.debug
import icu.h2l.login.HyperZoneLoginMain
import icu.h2l.login.config.VServerConfig
import icu.h2l.login.vServer.outpre.vc.OutPreRegisteredServer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 定时对 outpre 认证后端做 ServerPing 探测：
 * - 记录后端在线状态与协议版本
 * - 连续失败达到阈值后判定后端离线，此时新玩家接入会被拒绝
 */
class OutPreBackendHealthChecker(
    private val server: ProxyServer,
) {
    enum class Health {
        UNKNOWN,
        ONLINE,
        OFFLINE,
    }

    private val logger
        get() = HyperZoneLoginMain.getInstance().logger

    @Volatile
    var health: Health = Health.UNKNOWN
        private set

    /** 最近一次成功 ping 得到的后端版本（协议号 + 版本名）；离线后保留最后已知值，供断开提示等场景使用 */
    @Volatile
    var backendVersion: ServerPing.Version? = null
        private set

    @Volatile
    private var scheduledTask: ScheduledTask? = null
    private val consecutiveFailures = AtomicInteger(0)
    private val pingInFlight = AtomicBoolean(false)

    /** 复用 ping 目标；配置热重载后地址变化时重建 */
    @Volatile
    private var pingTarget: OutPreRegisteredServer? = null

    /** 未启用探测或尚未得出离线结论（UNKNOWN/ONLINE）时都允许接入 */
    fun isBackendUsable(): Boolean {
        if (!config().healthCheckEnabled) {
            return true
        }
        return health != Health.OFFLINE
    }

    fun start(plugin: Any) {
        val config = config()
        if (!config.healthCheckEnabled) {
            return
        }
        if (scheduledTask != null) {
            return
        }
        val proxy = server as? VelocityServer
        if (proxy == null) {
            logger.warn("OutPre backend health check requires VelocityServer runtime; health check disabled")
            return
        }
        val intervalSeconds = config.healthCheckIntervalSeconds.coerceAtLeast(1L)
        scheduledTask = server.scheduler
            .buildTask(plugin, Runnable { runCheck(proxy) })
            .repeat(intervalSeconds, TimeUnit.SECONDS)
            .schedule()
        logger.info(
            "OutPre backend health check started: interval={}s timeout={}ms failureThreshold={}",
            intervalSeconds,
            config.healthCheckTimeoutMillis,
            config.healthCheckFailureThreshold.coerceAtLeast(1),
        )
    }

    fun stop() {
        scheduledTask?.cancel()
        scheduledTask = null
    }

    private fun runCheck(proxy: VelocityServer) {
        val config = config()
        val address = config.resolveOutpreAuthAddress() ?: return
        if (!pingInFlight.compareAndSet(false, true)) {
            return
        }
        val target = pingTarget?.takeIf { it.serverInfo.address == address }
            ?: OutPreRegisteredServer(proxy, ServerInfo(config.outpreAuthTargetLabel(), address))
                .also { pingTarget = it }
        val options = PingOptions.builder()
            .timeout(config.healthCheckTimeoutMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .build()
        runCatching {
            target.ping(options).whenComplete { ping, throwable ->
                pingInFlight.set(false)
                if (throwable != null || ping == null) {
                    onPingFailure(throwable)
                } else {
                    onPingSuccess(ping)
                }
            }
        }.onFailure { throwable ->
            pingInFlight.set(false)
            onPingFailure(throwable)
        }
    }

    private fun onPingSuccess(ping: ServerPing) {
        consecutiveFailures.set(0)
        val version: ServerPing.Version? = ping.version
        val previousVersion = backendVersion
        val previousHealth = health
        backendVersion = version
        health = Health.ONLINE
        debug(HyperZoneDebugType.OUTPRE_TRACE) {
            "outpre.healthCheck ping-ok version=${version?.name} protocol=${version?.protocol}"
        }
        if (previousHealth != Health.ONLINE) {
            logger.info(
                "OutPre auth backend is online: version={} protocol={}",
                version?.name ?: "unknown",
                version?.protocol ?: -1,
            )
        } else if (version != null && previousVersion != null && version.protocol != previousVersion.protocol) {
            logger.info(
                "OutPre auth backend protocol changed: {} ({}) -> {} ({})",
                previousVersion.name,
                previousVersion.protocol,
                version.name,
                version.protocol,
            )
        }
    }

    private fun onPingFailure(throwable: Throwable?) {
        val failures = consecutiveFailures.incrementAndGet()
        val threshold = config().healthCheckFailureThreshold.coerceAtLeast(1)
        debug(HyperZoneDebugType.OUTPRE_TRACE) {
            "outpre.healthCheck ping-failed failures=$failures/$threshold reason=${throwable?.message}"
        }
        if (failures >= threshold && health != Health.OFFLINE) {
            health = Health.OFFLINE
            logger.warn(
                "OutPre auth backend is offline after {} consecutive ping failures: {}",
                failures,
                throwable?.message ?: "no response",
            )
        }
    }

    private fun config(): VServerConfig.OutpreConfig {
        return HyperZoneLoginMain.getCoreConfig().vServer.outpre
    }
}
