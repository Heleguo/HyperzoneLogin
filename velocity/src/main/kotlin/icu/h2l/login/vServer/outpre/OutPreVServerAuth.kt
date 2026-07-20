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

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.event.player.ServerPreConnectEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.proxy.server.ServerInfo
import com.velocitypowered.proxy.connection.client.ConnectedPlayer
import com.velocitypowered.proxy.server.VelocityRegisteredServer
import icu.h2l.api.event.area.PlayerAreaTransitionReason
import icu.h2l.api.event.auth.AuthenticationFailureEvent
import icu.h2l.api.event.vServer.VServerAuthStartEvent
import icu.h2l.api.event.vServer.VServerJoinEvent
import icu.h2l.api.log.HyperZoneDebugType
import icu.h2l.api.log.debug
import icu.h2l.api.message.HyperZoneMessagePlaceholder
import icu.h2l.api.player.getChannel
import icu.h2l.api.vServer.HyperZoneVServerAdapter
import icu.h2l.login.HyperZoneLoginMain
import icu.h2l.login.listener.PlayerAreaLifecycleListener
import icu.h2l.login.manager.HyperZonePlayerManager
import icu.h2l.login.message.MessageKeys
import icu.h2l.login.player.VelocityHyperZonePlayer
import icu.h2l.login.vServer.outpre.handler.OutPreAuthSessionHandler
import icu.h2l.login.vServer.outpre.session.BridgeWaitingAreaJoinSession
import icu.h2l.login.vServer.outpre.session.OutPreInitialJoinSession
import icu.h2l.login.vServer.outpre.session.OutPreInitialJoinSessionFactory
import icu.h2l.login.vServer.outpre.session.StruckInitialJoinSession
import io.netty.channel.Channel
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull

/**
 * outpre = 在正常 Velocity 注册前，先把客户端连接桥接到真实认证服；
 * 认证完成后再恢复真正的 Velocity 登录尾段。
 */
class OutPreVServerAuth(
    private val server: ProxyServer,
) : HyperZoneVServerAdapter {
    private val logger
        get() = HyperZoneLoginMain.getInstance().logger
    private val states = ConcurrentHashMap<Channel, OutPreState>()
    private val initialJoinSessions = ConcurrentHashMap<Channel, OutPreInitialJoinSession>()

    var registeredServer: RegisteredServer? = null
        private set

    fun init(plugin: Any) {
        val authAddress = configuredAuthAddress()
            ?: throw IllegalStateException("OutPre auth endpoint is not configured")
        val proxy = server as? com.velocitypowered.proxy.VelocityServer
            ?: throw IllegalStateException("OutPre requires VelocityServer runtime")
        val authTargetLabel = configuredAuthTargetLabel()
        val outPreServerInfo = ServerInfo(authTargetLabel, authAddress)
        server.registerServer(outPreServerInfo)
//        强行转换 如果不是VelocityServer，说明有问题
        val getServer = proxy.getServer(configuredAuthTargetLabel())
        registeredServer = getServer.getOrNull()

    }

    internal fun trace(message: String) {
        debug(HyperZoneDebugType.OUTPRE_TRACE, message)
    }

    internal fun describeState(state: OutPreState?): String {
        if (state == null) {
            return "state=null"
        }
        return "state(inAuthHold=${state.inAuthHold}, hasConnectedToAuthServerOnce=${state.hasConnectedToAuthServerOnce}, verifiedExitPending=${state.verifiedExitPending}, initialFlowPending=${state.initialFlowPending}, returnTarget=${state.returnTargetServerName}, authTarget=${state.authTargetLabel})"
    }

    override fun isEnabled(): Boolean {
        return configuredAuthAddress() != null
    }

    // ---- 桥接创建 / 初始化入口 ----

    fun createBridge(player: ConnectedPlayer): OutPreBackendBridge {
        val authAddress = configuredAuthAddress()
            ?: throw IllegalStateException("OutPre auth endpoint is not configured")
        val proxy = server as? com.velocitypowered.proxy.VelocityServer
            ?: throw IllegalStateException("OutPre requires VelocityServer runtime")
        return OutPreBackendBridge(
            proxy, authAddress, player, this,
            registeredServer as VelocityRegisteredServer
        )
    }

    fun beginInitialJoin(player: ConnectedPlayer, handler: OutPreAuthSessionHandler) {
        val messages = HyperZoneLoginMain.getInstance().messageService
        val hyperPlayer = getHyperPlayer(player) ?: return
        trace(
            "beginInitialJoin start channel=${
                player.getChannel().id()
            } player=${player.username} waitingArea=${hyperPlayer.isInWaitingArea()} verified=${hyperPlayer.isVerified()} attachedProfile=${hyperPlayer.hasAttachedProfile()}"
        )

        runCatching {
            hyperPlayer.injectProxyPlayer(player)
        }.onFailure { throwable ->
            logger.debug("outpre 绑定代理玩家跳过: ${throwable.message}")
        }

        if (configuredAuthAddress() == null) {
            player.disconnect(messages.render(player, MessageKeys.BackendAuth.MISCONFIGURED_DISCONNECT))
            return
        }

        val outpreConfig = HyperZoneLoginMain.getCoreConfig().vServer.outpre
        val isStruck = outpreConfig.struckOnlineMode && hyperPlayer.isOnlinePlayer
        val session = OutPreInitialJoinSessionFactory.create(
            struck = isStruck,
            player = player,
            hyperPlayer = hyperPlayer,
            handler = handler,
            authTargetLabel = configuredAuthTargetLabel(),
        )
        val state = session.state
        states[player.getChannel()] = state
        initialJoinSessions[player.getChannel()] = session
        hyperPlayer.suspendMessageDelivery()
        trace(
            "beginInitialJoin state-created channel=${player.getChannel().id()} player=${player.username} struck=$isStruck ${
                describeState(state)
            }"
        )

        if (!player.isActive) {
            trace(
                "beginInitialJoin player-inactive channel=${
                    player.getChannel().id()
                } player=${player.username} clearing-initial-state"
            )
            discardInitialJoinState(player, state, hyperPlayer)
            return
        }

        if (!hyperPlayer.isInWaitingArea()) {
            state.inAuthHold = false
            state.verifiedExitPending = true
            trace(
                "beginInitialJoin already-ready-after-authStart channel=${
                    player.getChannel().id()
                } player=${player.username} ${
                    describeState(
                        state
                    )
                }"
            )
        }
        session.begin(this)
    }

    fun finishInitialBridgePhase(player: ConnectedPlayer) {
        // 初始 outpre 登录阶段已经释放到 Velocity 正常链路，桥接职责到此结束。
        initialJoinSessions.remove(player.getChannel())?.handler?.bridge?.disconnect()
        states.computeIfPresent(player.getChannel()) { _, state ->
            state.initialFlowPending = false
            state
        }
    }

    fun resolveReleaseTarget(player: ConnectedPlayer, preferredTargetServerName: String?): RegisteredServer? {
        val authTargetLabel = states[player.getChannel()]?.authTargetLabel ?: configuredAuthTargetLabel()
        val resolvedTargetName = preferredTargetServerName
            ?.takeUnless { it.isBlank() || it.equals(authTargetLabel, ignoreCase = true) }
            ?.takeIf { server.getServer(it).isPresent }
            ?: resolveFallbackTargetServerName(authTargetLabel)
        return resolvedTargetName?.let { server.getServer(it).orElse(null) }
    }

    override fun reJoin(player: Player) {
        val messages = HyperZoneLoginMain.getInstance().messageService
        val hyperPlayer = getHyperPlayer(player) ?: return
        if (configuredAuthAddress() == null) {
            player.sendMessage(messages.render(player, MessageKeys.BackendAuth.NO_AUTH_SERVER))
            return
        }

        val authStartEvent = VServerAuthStartEvent(player, hyperPlayer)
        server.eventManager.fire(authStartEvent).join()
        if (!hyperPlayer.isInWaitingArea()) {
            return
        }

        logger.warn("OutPre reJoin is not available for direct auth endpoint mode: player={}", player.username)
        player.sendMessage(messages.render(player, MessageKeys.HzlCommand.AUTH_FLOW_UNAVAILABLE))
    }

    override fun isPlayerInWaitingArea(player: Player): Boolean {
        val state = states[player.getChannel()]
        return state != null && (
                state.inAuthHold ||
                        !state.hasConnectedToAuthServerOnce ||
                        state.initialFlowPending
                )
    }

    override fun supportsProxyFallbackCommands(): Boolean {
        return true
    }

    override fun allowsProxyFallbackCommand(player: Player): Boolean {
        return states.containsKey(player.getChannel())
    }

    override fun exitWaitingArea(player: Player): Boolean {
        val state = states[player.getChannel()] ?: return false
        if (state.initialFlowPending) {
            player.disconnect(Component.translatable("disconnect.closed"))
            return true
        }

        PlayerAreaLifecycleListener.markWaitingAreaLeavePending(player, PlayerAreaTransitionReason.EXIT_REQUEST)
        return connectPlayerToTarget(
            player = player,
            targetServerName = state.returnTargetServerName,
            authServerName = state.authTargetLabel,
            missingTargetKey = MessageKeys.BackendAuth.EXIT_NO_TARGET,
            missingServerKey = MessageKeys.BackendAuth.EXIT_SERVER_MISSING,
            failureExceptionKey = MessageKeys.BackendAuth.EXIT_FAILURE_EXCEPTION,
            failureReasonKey = MessageKeys.BackendAuth.EXIT_FAILURE_REASON,
        )
    }

    override fun onVerified(player: Player) {
        val state = states[player.getChannel()] ?: return
        val session = initialJoinSessions[player.getChannel()]
        trace(
            "outpre.onVerified before channel=${player.getChannel().id()} player=${player.username} ${
                describeState(
                    state
                )
            }"
        )
        state.inAuthHold = false

        if (state.initialFlowPending && session != null) {
            session.onVerified(this)
            return
        }

        if (!state.hasConnectedToAuthServerOnce) {
            state.verifiedExitPending = true
            trace(
                "outpre.onVerified deferred-no-auth-join channel=${
                    player.getChannel().id()
                } player=${player.username} ${
                    describeState(
                        state
                    )
                }"
            )
            return
        }

        trace(
            "outpre.onVerified connect-verified-target channel=${player.getChannel().id()} player=${player.username} ${
                describeState(
                    state
                )
            }"
        )
        connectVerifiedPlayerToResolvedTarget(player, state)
    }

    // ---- 运行时事件 ----

    @Subscribe
    fun onServerPreConnect(event: ServerPreConnectEvent) {
        val player = event.player
        val messages = HyperZoneLoginMain.getInstance().messageService
        val state = states[player.getChannel()] ?: return
        if (!needsAuthServerProtection(state)) return

        val requestedServerName = event.originalServer.serverInfo.name
        if (rememberRequestedServerDuringAuth()) {
            state.returnTargetServerName = requestedServerName
        }

        player.sendMessage(messages.render(player, MessageKeys.BackendAuth.MUST_VERIFY_BEFORE_TRANSFER))
        event.result = ServerPreConnectEvent.ServerResult.denied()
    }

    @Subscribe
    fun onServerConnected(event: ServerPostConnectEvent) {
        val player = event.player
        val state = states[player.getChannel()] ?: return
        if (!state.inAuthHold && !state.initialFlowPending) {
            states.remove(player.getChannel(), state)
            initialJoinSessions.remove(player.getChannel())
        }
    }

    @Subscribe
    fun onDisconnect(event: DisconnectEvent) {
        states.remove(event.player.getChannel())
        initialJoinSessions.remove(event.player.getChannel())?.handler?.bridge?.disconnect()
    }

    @Subscribe
    fun onAuthenticationFailure(event: AuthenticationFailureEvent) {
        initialJoinSessions.values
            .filterIsInstance<StruckInitialJoinSession>()
            .firstOrNull { it.matchesFailure(event) }
            ?.onAuthenticationFailure(this, event)
    }

    // ---- 模式会话协作 ----

    /**
     * 启动到认证服的初始桥接连接。
     */
    internal fun startAuthBridgeJoin(
        player: ConnectedPlayer,
        hyperPlayer: VelocityHyperZonePlayer,
        bridge: OutPreBackendBridge,
        state: OutPreState,
    ) {
        val messages = HyperZoneLoginMain.getInstance().messageService

        bridge.connect().whenCompleteAsync({ _, throwable ->
            if (throwable != null) {
                initialJoinSessions.remove(player.getChannel())
                states.remove(player.getChannel(), state)
                hyperPlayer.resumeMessageDelivery()
                player.sendMessage(
                    messages.render(
                        player,
                        MessageKeys.BackendAuth.ENTER_FAILED_EXCEPTION,
                        HyperZoneMessagePlaceholder.text("reason", throwable.message ?: "Unknown error"),
                    )
                )
                player.disconnect(Component.text("OutPre auth backend connection failed", NamedTextColor.RED))
                return@whenCompleteAsync
            }
        }, player.connection.eventLoop())
    }

    /**
     * 丢弃尚未真正开始的初始进入会话。
     */
    private fun discardInitialJoinState(
        player: ConnectedPlayer,
        state: OutPreState,
        hyperPlayer: VelocityHyperZonePlayer,
    ) {
        initialJoinSessions.remove(player.getChannel())?.handler?.bridge?.disconnect()
        states.remove(player.getChannel(), state)
        hyperPlayer.resumeMessageDelivery()
    }

    fun onInitialBridgeJoined(bridge: OutPreBackendBridge, player: ConnectedPlayer) {
        val session = initialJoinSessions[player.getChannel()] as? BridgeWaitingAreaJoinSession ?: return
        if (!session.matchesBridge(bridge)) {
            return
        }
        session.onAuthServerJoined(this)
    }

    fun onInitialBridgeDisconnected(bridge: OutPreBackendBridge, player: ConnectedPlayer, reason: String?) {
        val session = initialJoinSessions[player.getChannel()] ?: return
        if (!session.matchesBridge(bridge)) {
            return
        }
        val state = states.remove(player.getChannel())
        initialJoinSessions.remove(player.getChannel())
        if (player.isActive) {
            player.disconnect(Component.text(reason ?: "OutPre auth bridge disconnected", NamedTextColor.RED))
        }
        if (state != null) {
            logger.warn(
                "OutPre initial backend bridge disconnected before verification: player={}, reason={}",
                player.username,
                reason
            )
        }
    }

    /**
     * 向等待区链路广播“已经进入认证服/等待区”的加入事件。
     */
    internal fun publishWaitingAreaJoinEvent(player: Player, hyperPlayer: VelocityHyperZonePlayer) {
        server.eventManager.fire(VServerJoinEvent(player, hyperPlayer))
    }

    /**
     * 向认证模块广播“现在允许以等待区视角接管认证”的开始事件。
     */
    internal fun publishAuthStartEvent(
        player: Player,
        hyperPlayer: VelocityHyperZonePlayer,
        state: OutPreState,
        source: String
    ) {
        val authStartEvent = VServerAuthStartEvent(player, hyperPlayer)
        server.eventManager.fire(authStartEvent)
        trace(
            "$source after-authStart channel=${
                player.getChannel().id()
            } player=${player.username} waitingArea=${hyperPlayer.isInWaitingArea()} verified=${hyperPlayer.isVerified()} attachedProfile=${hyperPlayer.hasAttachedProfile()} ${
                describeState(
                    state
                )
            }"
        )
    }

    /**
     * 处理“初始进入阶段已完成验证”后的后续动作：
     * - 如果仍处于初始登录桥接期，就继续完成 outpre 释放；
     * - 否则按已验证玩家直接转发到目标服。
     */
    internal fun continueVerifiedInitialJoin(
        player: Player,
        handler: OutPreAuthSessionHandler,
        state: OutPreState
    ) {
        trace(
            "outpre.onVerified completing-initial-flow channel=${
                player.getChannel().id()
            } player=${player.username} target=${state.returnTargetServerName} ${
                describeState(
                    state
                )
            }"
        )
        if (state.initialFlowPending) {
            handler.completeAfterVerification(state.returnTargetServerName)
        } else {
            connectVerifiedPlayerToResolvedTarget(player, state)
        }
    }

    /**
     * struck 模式未能及时完成线上认证时，切换为“进入认证服等待区”的桥接模式。
     */
    internal fun switchStruckSessionToBridgeWaitingArea(
        session: StruckInitialJoinSession,
        event: AuthenticationFailureEvent
    ) {
        val player = session.player
        if (!player.isActive) {
            return
        }
        val fallbackSession = OutPreInitialJoinSessionFactory.waitingAreaFallbackFrom(session)
        initialJoinSessions[player.getChannel()] = fallbackSession
        trace(
            "outpre.struck fallback-to-waiting-area channel=${player.getChannel().id()} player=${player.username} reason=${event.reason} message=${event.reasonMessage} ${
                describeState(
                    fallbackSession.state
                )
            }"
        )
        startAuthBridgeJoin(player, fallbackSession.hyperPlayer, fallbackSession.handler.bridge, fallbackSession.state)
    }

    // ---- 目标服转发 ----

    private fun connectVerifiedPlayerToResolvedTarget(player: Player, state: OutPreState): Boolean {
        initialJoinSessions.remove(player.getChannel())?.handler?.bridge?.disconnect()
        states.remove(player.getChannel(), state)
        return connectPlayerToTarget(
            player = player,
            targetServerName = state.returnTargetServerName,
            authServerName = state.authTargetLabel,
            missingTargetKey = MessageKeys.BackendAuth.VERIFIED_NO_TARGET,
            missingServerKey = MessageKeys.BackendAuth.VERIFIED_SERVER_MISSING,
            failureExceptionKey = MessageKeys.BackendAuth.VERIFIED_FAILURE_EXCEPTION,
            failureReasonKey = MessageKeys.BackendAuth.VERIFIED_FAILURE_REASON,
        )
    }

    private fun connectPlayerToTarget(
        player: Player,
        targetServerName: String?,
        authServerName: String,
        missingTargetKey: String,
        missingServerKey: String,
        failureExceptionKey: String,
        failureReasonKey: String,
    ): Boolean {
        val messages = HyperZoneLoginMain.getInstance().messageService
        val resolvedTarget = targetServerName
            ?.takeUnless { it.isBlank() || it.equals(authServerName, ignoreCase = true) }
            ?: resolveFallbackTargetServerName(authServerName)
        val hyperPlayer = getHyperPlayer(player)

        if (resolvedTarget == null) {
            PlayerAreaLifecycleListener.clearPendingWaitingAreaLeave(player)
            hyperPlayer?.resumeMessageDelivery()
            player.sendMessage(messages.render(player, missingTargetKey))
            return false
        }

        val target = server.getServer(resolvedTarget).orElse(null)
        if (target == null) {
            PlayerAreaLifecycleListener.clearPendingWaitingAreaLeave(player)
            hyperPlayer?.resumeMessageDelivery()
            player.sendMessage(
                messages.render(
                    player,
                    missingServerKey,
                    HyperZoneMessagePlaceholder.text("server", resolvedTarget),
                )
            )
            return false
        }

        hyperPlayer?.suspendMessageDelivery()
        player.createConnectionRequest(target).connect().whenComplete { result, throwable ->
            if (throwable != null) {
                PlayerAreaLifecycleListener.clearPendingWaitingAreaLeave(player)
                hyperPlayer?.resumeMessageDelivery()
                player.sendMessage(
                    messages.render(
                        player,
                        failureExceptionKey,
                        HyperZoneMessagePlaceholder.text("reason", throwable.message ?: "Unknown error"),
                    )
                )
                return@whenComplete
            }

            if (result == null || !result.isSuccessful) {
                PlayerAreaLifecycleListener.clearPendingWaitingAreaLeave(player)
                hyperPlayer?.resumeMessageDelivery()
                val reason = result.reasonComponent?.map { it.toString() }?.orElse("未知原因") ?: "未知原因"
                player.sendMessage(
                    messages.render(
                        player,
                        failureReasonKey,
                        HyperZoneMessagePlaceholder.text("reason", reason),
                    )
                )
            }
        }
        return true
    }

    // ---- 状态 / 配置辅助 ----

    private fun needsAuthServerProtection(state: OutPreState): Boolean {
        return state.inAuthHold || !state.hasConnectedToAuthServerOnce || state.initialFlowPending
    }

    private fun getHyperPlayer(player: Player): VelocityHyperZonePlayer? {
        return HyperZonePlayerManager.getByPlayerOrNull(player)
    }

    private fun configuredAuthAddress(): java.net.InetSocketAddress? {
        return HyperZoneLoginMain.getCoreConfig().vServer.outpre.resolveOutpreAuthAddress()
    }

    private fun configuredAuthTargetLabel(): String {
        return HyperZoneLoginMain.getCoreConfig().vServer.outpre.outpreAuthTargetLabel()
    }

    private fun rememberRequestedServerDuringAuth(): Boolean {
        return HyperZoneLoginMain.getCoreConfig().vServer.rememberRequestedServerDuringAuth
    }

    private fun resolveFallbackTargetServerName(authServerName: String): String? {
        val directConfiguredTarget = HyperZoneLoginMain.getCoreConfig().vServer.postAuthDefaultServer
            .trim()
            .takeUnless { it.isBlank() || it.equals(authServerName, ignoreCase = true) }
            ?.takeIf { server.getServer(it).isPresent }
        if (directConfiguredTarget != null) {
            return directConfiguredTarget
        }

        return null
    }

}
