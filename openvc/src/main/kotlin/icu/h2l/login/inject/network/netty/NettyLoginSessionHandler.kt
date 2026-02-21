package icu.h2l.login.inject.network.netty

import com.google.common.primitives.Longs
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.event.connection.PreLoginEvent
import com.velocitypowered.api.event.permission.PermissionsSetupEvent
import com.velocitypowered.api.event.player.CookieReceiveEvent
import com.velocitypowered.api.event.player.GameProfileRequestEvent
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent
import com.velocitypowered.api.network.ProtocolVersion
import com.velocitypowered.api.proxy.crypto.IdentifiedKey
import com.velocitypowered.api.util.GameProfile
import com.velocitypowered.api.util.UuidUtils
import com.velocitypowered.proxy.VelocityServer
import com.velocitypowered.proxy.config.PlayerInfoForwarding
import com.velocitypowered.proxy.connection.MinecraftConnection
import com.velocitypowered.proxy.connection.VelocityConstants
import com.velocitypowered.proxy.connection.client.*
import com.velocitypowered.proxy.crypto.EncryptionUtils
import com.velocitypowered.proxy.crypto.IdentifiedKeyImpl
import com.velocitypowered.proxy.protocol.StateRegistry
import com.velocitypowered.proxy.protocol.netty.MinecraftDecoder
import com.velocitypowered.proxy.protocol.packet.*
import com.velocitypowered.proxy.util.VelocityProperties
import icu.h2l.api.event.connection.OnlineAuthEvent
import icu.h2l.api.event.connection.OpenPreLoginEvent
import icu.h2l.login.inject.network.NettyReflectionHelper
import icu.h2l.login.inject.network.NettyReflectionHelper.fireLogin
import icu.h2l.login.inject.network.VelocityNetworkInjectorImpl
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.checkerframework.checker.nullness.qual.MonotonicNonNull
import java.net.InetSocketAddress
import java.security.GeneralSecurityException
import java.security.KeyPair
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer
import java.util.function.Function

class NettyLoginSessionHandler(
    private val injector: VelocityNetworkInjectorImpl,
    private val mcConnection: MinecraftConnection,
    private val channel: Channel,
) : ChannelInboundHandlerAdapter() {
    companion object {
        val logger: Logger = LogManager.getLogger(InitialLoginSessionHandler::class.java)
    }

    private lateinit var sessionHandler: InitialLoginSessionHandler
    private lateinit var inbound: LoginInboundConnection
    private var forceKeyAuthentication: Boolean = false
    private lateinit var login: @MonotonicNonNull ServerLoginPacket
    private var verify: ByteArray = VelocityConstants.EMPTY_BYTE_ARRAY
    private lateinit var connectedPlayer: ConnectedPlayer
    private lateinit var profile: GameProfile
    private var onlineMode: Boolean = true


    override fun channelRead(ctx: ChannelHandlerContext, msg: Any?) {
        initFields()

        when (msg) {
            is ServerLoginPacket -> handleServerLogin(ctx, msg)
            is EncryptionResponsePacket -> handleEncryptionResponse(ctx, msg)
            is LoginAcknowledgedPacket -> handleLoginAcknowledgedPacket(ctx, msg)
            is ServerboundCookieResponsePacket -> handleServerboundCookieResponsePacket(ctx, msg)
            else -> super.channelRead(ctx, msg)
        }
    }

    private fun initFields() {
        if (::sessionHandler.isInitialized) {
            return
        }

        val sessionHandler = mcConnection.activeSessionHandler as? InitialLoginSessionHandler ?: return
        this.sessionHandler = sessionHandler

        this.inbound = InitialLoginSessionHandler::class.java.getDeclaredField("inbound").also {
            it.isAccessible = true
        }.get(sessionHandler) as LoginInboundConnection

        forceKeyAuthentication = VelocityProperties.readBoolean(
            "auth.forceSecureProfiles",
            injector.proxy.configuration.isForceKeyAuthentication
        )
    }

    private fun retire() {
        channel.pipeline().remove(this)
    }

    private fun generateEncryptionRequest(): EncryptionRequestPacket {
        val verify = ByteArray(4)
        ThreadLocalRandom.current().nextBytes(verify)

        val request = EncryptionRequestPacket()
        request.publicKey = injector.proxy.serverKeyPair.public.encoded
        request.verifyToken = verify
        return request
    }


    //    0-LOGIN_PACKET_EXPECTED 1-LOGIN_PACKET_RECEIVED 2-ENCRYPTION_REQUEST_SENT 3-ENCRYPTION_RESPONSE_RECEIVED
//    4-START 5-SUCCESS_SENT 6-ACKNOWLEDGED
    private var cState = 0
    private fun aState(expectedState: Int) {
        if (this.cState != expectedState) {
            if (MinecraftDecoder.DEBUG) {
                logger.error(
                    "{} Received an unexpected packet requiring state {}, but we are in {}",
                    inbound,
                    expectedState, this.cState
                )
            }
            mcConnection.close(true)
        }
    }

    private val server get() = injector.proxy

    private fun handleServerLogin(ctx: ChannelHandlerContext, packet: ServerLoginPacket) {
        aState(0)
        cState = 1
        val playerKey: IdentifiedKey? = packet.playerKey
        if (playerKey != null) {
            if (playerKey.hasExpired()) {
                inbound.disconnect(Component.translatable("multiplayer.disconnect.invalid_public_key_signature"))
                return
            }
            val isKeyValid: Boolean =
                if (playerKey.keyRevision == IdentifiedKey.Revision.LINKED_V2 && playerKey is IdentifiedKeyImpl) {
                    playerKey.internalAddHolder(packet.holderUuid)
                } else {
                    playerKey.isSignatureValid
                }

            if (!isKeyValid) {
                inbound.disconnect(Component.translatable("multiplayer.disconnect.invalid_public_key"))
            }
        } else if (mcConnection.protocolVersion.noLessThan(ProtocolVersion.MINECRAFT_1_19)
            && forceKeyAuthentication
            && mcConnection.protocolVersion.lessThan(ProtocolVersion.MINECRAFT_1_19_3)
        ) {
            inbound.disconnect(Component.translatable("multiplayer.disconnect.missing_public_key"))
        }
        inbound.setPlayerKey(playerKey)
        this.login = packet

        val event = PreLoginEvent(inbound, login.getUsername(), login.holderUuid)
        injector.proxy.eventManager.fire(event).thenRunAsync({
            if (mcConnection.isClosed) {
                // The player was disconnected
                return@thenRunAsync
            }
            val result = event.result
            val disconnectReason = result.reasonComponent
            if (disconnectReason.isPresent) {
                // The component is guaranteed to be provided if the connection was denied.
                inbound.disconnect(disconnectReason.get())
                return@thenRunAsync
            }

            inbound.fireLogin {
                if (mcConnection.isClosed) {
                    // The player was disconnected
                    return@fireLogin
                }
                mcConnection.eventLoop().execute {

                    val holderUuid: UUID? = login.holderUuid
                    val userName: String = login.getUsername()
                    val host = if (inbound.rawVirtualHost.isPresent) inbound.rawVirtualHost.get() else ""

                    val openPreLoginEvent = OpenPreLoginEvent(holderUuid!!, userName, host)
                    injector.proxy.eventManager.fire(openPreLoginEvent).thenRun {
                        onlineMode = openPreLoginEvent.isOnline
                        if (openPreLoginEvent.isOnline) {
                            val request: EncryptionRequestPacket = generateEncryptionRequest()
                            this.verify = request.verifyToken.copyOf(4)
                            mcConnection.write(request)
                            cState = 2

//                            this.currentState = InitialLoginSessionHandler.LoginState.ENCRYPTION_REQUEST_SENT;
                        } else {
                            doLogin(false, "", null)
                        }
                    }.exceptionally { ex: Throwable? ->
                        logger.error("Exception in pre-login stage", ex)
                        null
                    }
                }
            }
        }, mcConnection.eventLoop()).exceptionally { ex: Throwable? ->
            logger.error("Exception in pre-login stage", ex)
            null
        }
    }

    private fun doLogin(online: Boolean, serverId: String?, decryptedSharedSecret: ByteArray?) {


        val remoteAddress = mcConnection.remoteAddress as InetSocketAddress
        val playerIp = remoteAddress.hostString
        val onlineAuthEvent = OnlineAuthEvent(
            login.getUsername(),
            login.holderUuid!!,
            serverId!!,
            playerIp,
            remoteAddress,
            online
        )
        val preProfile = GameProfile(login.holderUuid, login.username, Collections.emptyList())
        connectedPlayer = NettyReflectionHelper.createConnectedPlayer(
            injector.proxy,
            preProfile,
            mcConnection,
            inbound.virtualHost.orElse(null),
            inbound.rawVirtualHost.orElse(null),
            online,
            inbound.handshakeIntent,
            inbound.identifiedKey,
        )
        onlineAuthEvent.gameProfile = preProfile
        onlineAuthEvent.player = connectedPlayer

        injector.proxy.eventManager.fire(onlineAuthEvent).thenRunAsync(
            {
                if (mcConnection.isClosed) {
                    // The player disconnected after we authenticated them.
                    return@thenRunAsync
                }
                // Go ahead and enable encryption. Once the client sends EncryptionResponse, encryption
                // is enabled.
                try {
                    if (online) {
//                            logger.info(
//                                    "已开启加密为 {} ({})",
//                                    login.getUsername(), playerIp);
                        mcConnection.enableEncryption(decryptedSharedSecret)
                    }
                } catch (e: GeneralSecurityException) {
                    logger.error("Unable to enable encryption for connection", e)
                    // At this point, the connection is encrypted, but something's wrong on our side and
                    // we can't do anything about it.
                    mcConnection.close(true)
                    return@thenRunAsync
                }

                profile = onlineAuthEvent.gameProfile
                    ?: connectedPlayer.gameProfile

                if (onlineAuthEvent.player !== connectedPlayer) {
                    logger.warn("OnlineAuthEvent.player 被替换，忽略替换并继续使用预创建 ConnectedPlayer")
                }

                activatedAuthSession()
            }, mcConnection.eventLoop()
        ).exceptionally { ex: Throwable? ->
            logger.error("Exception in login stage", ex)
            null
        }
    }

    private fun handleEncryptionResponse(ctx: ChannelHandlerContext, packet: EncryptionResponsePacket) {
        aState(2)
        cState = 3
        check(this::login.isInitialized) { "No ServerLogin packet received yet." }
        this.login

        check(verify.isNotEmpty()) { "No EncryptionRequest packet sent yet." }


        try {
            val serverKeyPair: KeyPair = server.serverKeyPair
            if (inbound.identifiedKey != null) {
                val playerKey = inbound.identifiedKey
                check(
                    playerKey!!.verifyDataSignature(
                        packet.verifyToken, verify,
                        Longs.toByteArray(packet.getSalt())
                    )
                ) { "Invalid client public signature." }
            } else {
                val decryptedVerifyToken = EncryptionUtils.decryptRsa(serverKeyPair, packet.verifyToken)
                check(
                    MessageDigest.isEqual(
                        verify,
                        decryptedVerifyToken
                    )
                ) { "Unable to successfully decrypt the verification token." }
            }

            val decryptedSharedSecret = EncryptionUtils.decryptRsa(serverKeyPair, packet.sharedSecret)
            val serverId = EncryptionUtils.generateServerId(decryptedSharedSecret, serverKeyPair.public)

            doLogin(true, serverId, decryptedSharedSecret)
        } catch (e: Throwable) {
            logger.error("认证出错", e)
            mcConnection.close(true)
        }
    }


    fun handleLoginAcknowledgedPacket(ctx: ChannelHandlerContext, packet: LoginAcknowledgedPacket?) {
        aState(5)
        cState = 6

        mcConnection.setActiveSessionHandler(
            StateRegistry.CONFIG,
            ClientConfigSessionHandler(server, connectedPlayer)
        )

        server.getEventManager().fire<PostLoginEvent?>(PostLoginEvent(connectedPlayer))
            .thenCompose<Void?>(Function { ignored: PostLoginEvent? -> connectToInitialServer(connectedPlayer) })
            .exceptionally(Function { ex: Throwable? ->
                logger.error(
                    "Exception while connecting {} to initial server",
                    connectedPlayer,
                    ex
                )
                null
            })
    }

    private fun connectToInitialServer(player: ConnectedPlayer): CompletableFuture<Void?> {
        val initialFromConfig = player.getNextServerToTry()
        val event =
            PlayerChooseInitialServerEvent(player, initialFromConfig.orElse(null))

        return server.getEventManager().fire(event).thenRunAsync(
            {
                val toTry = event.getInitialServer()
                if (toTry.isEmpty()) {
                    player.disconnect0(
                        Component.translatable("velocity.error.no-available-servers", NamedTextColor.RED),
                        true
                    )
                    return@thenRunAsync
                }
                player.createConnectionRequest(toTry.get()).fireAndForget()
            }, mcConnection.eventLoop()
        )
    }

    fun handleServerboundCookieResponsePacket(ctx: ChannelHandlerContext, packet: ServerboundCookieResponsePacket) {
        server.getEventManager()
            .fire(CookieReceiveEvent(connectedPlayer, packet.getKey(), packet.getPayload()))
            .thenAcceptAsync(Consumer { event: CookieReceiveEvent? ->
                check(
                    !event!!.getResult().isAllowed()
                ) { "A cookie was requested by a proxy plugin in login phase but the response wasn't handled" }
            }, mcConnection.eventLoop())
    }

    fun activatedAuthSession() {
        // Some connection types may need to alter the game profile.
        profile = mcConnection.getType().addGameProfileTokensIfRequired(
            profile,
            server.getConfiguration().getPlayerInfoForwardingMode()
        )
        val profileRequestEvent = GameProfileRequestEvent(
            inbound, profile,
            onlineMode
        )
        val finalProfile: GameProfile = profile

        server.getEventManager().fire(profileRequestEvent)
            .thenComposeAsync({ profileEvent: GameProfileRequestEvent? ->
                if (mcConnection.isClosed()) {
                    // The player disconnected after we authenticated them.
                    return@thenComposeAsync CompletableFuture.completedFuture<Void?>(null)
                }
                // Initiate a regular connection and move over to it.

                val player = connectedPlayer
                if (!server.canRegisterConnection(player)) {
                    player.disconnect0(
                        Component.translatable("velocity.error.already-connected-proxy", NamedTextColor.RED),
                        true
                    )
                    return@thenComposeAsync CompletableFuture.completedFuture<Void?>(null)
                }

                if (server.getConfiguration().isLogPlayerConnections()) {
                    logger.info("{} has connected", player)
                }

                server.getEventManager()
                    .fire(PermissionsSetupEvent(player, NettyReflectionHelper.getDefaultPermissionsProvider()))
                    .thenAcceptAsync(Consumer { event: PermissionsSetupEvent? ->
                        if (!mcConnection.isClosed()) {
                            // wait for permissions to load, then set the players permission function
                            val function = event!!.createFunction(player)
                            if (function == null) {
                                logger.error(
                                    ("A plugin permission provider {} provided an invalid permission "
                                            + "function for player {}. This is a bug in the plugin, not in "
                                            + "Velocity. Falling back to the default permission function."),
                                    event.getProvider().javaClass.getName(), player.getUsername()
                                )
                            } else {
                                NettyReflectionHelper.setPermissionFunction(player, function)
                            }
                            startLoginCompletion(player)
                        }
                    }, mcConnection.eventLoop())
            }, mcConnection.eventLoop()).exceptionally(Function { ex: Throwable? ->
                logger.error("Exception during connection of {}", finalProfile, ex)
                null
            })
    }

    private fun startLoginCompletion(player: ConnectedPlayer) {
        val threshold = server.getConfiguration().getCompressionThreshold()
        if (threshold >= 0 && mcConnection.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_8)) {
            mcConnection.write(SetCompressionPacket(threshold))
            mcConnection.setCompressionThreshold(threshold)
        }
        val configuration = server.getConfiguration()
        var playerUniqueId = player.getUniqueId()
        if (configuration.getPlayerInfoForwardingMode() == PlayerInfoForwarding.NONE) {
            playerUniqueId = UuidUtils.generateOfflinePlayerUuid(player.getUsername())
        }

        if (player.getIdentifiedKey() != null) {
            val playerKey = player.getIdentifiedKey()
            if (playerKey!!.getSignatureHolder() == null) {
                if (playerKey is IdentifiedKeyImpl) {
                    // Failsafe
                    if (!playerKey.internalAddHolder(player.getUniqueId())) {
                        if (onlineMode) {
                            inbound.disconnect(
                                Component.translatable("multiplayer.disconnect.invalid_public_key")
                            )
                            return
                        } else {
                            logger.warn(
                                "Key for player {} could not be verified!",
                                player.getUsername()
                            )
                        }
                    }
                } else {
                    logger.warn("A custom key type has been set for player " + player.getUsername())
                }
            } else {
                if (playerKey.getSignatureHolder() != playerUniqueId) {
                    logger.warn(
                        "UUID for Player {} mismatches! "
                                + "Chat/Commands signatures will not work correctly for this player!",
                        player.getUsername()
                    )
                }
            }
        }

        completeLoginProtocolPhaseAndInitialize(player)
    }


    private fun completeLoginProtocolPhaseAndInitialize(player: ConnectedPlayer) {
        mcConnection.setAssociation(player)

        server.getEventManager().fire<LoginEvent?>(LoginEvent(player)).thenAcceptAsync({ event: LoginEvent? ->
            if (mcConnection.isClosed()) {
                // The player was disconnected
                server.getEventManager().fireAndForget(
                    DisconnectEvent(
                        player,
                        DisconnectEvent.LoginStatus.CANCELLED_BY_USER_BEFORE_COMPLETE
                    )
                )
                return@thenAcceptAsync
            }
            val reason = event!!.getResult().getReasonComponent()
            if (reason.isPresent()) {
                player.disconnect0(reason.get(), true)
            } else {
                if (!server.registerConnection(player)) {
                    player.disconnect0(Component.translatable("velocity.error.already-connected-proxy"), true)
                    return@thenAcceptAsync
                }

                val success = ServerLoginSuccessPacket()
                success.setUsername(player.getUsername())
                success.setProperties(player.getGameProfileProperties())
                success.setUuid(player.getUniqueId())
                mcConnection.write(success)

                cState = 5
                if (inbound.getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_20_2)) {
                    cState = 6
//                    WIP
                    mcConnection.setActiveSessionHandler(
                        StateRegistry.PLAY,
                        NettyReflectionHelper.createInitialConnectSessionHandler(player, server)
                    )
                    server.getEventManager().fire<PostLoginEvent?>(PostLoginEvent(player))
                        .thenCompose<Void?>(Function { ignored: PostLoginEvent? -> connectToInitialServer(player) })
                        .exceptionally(
                            Function { ex: Throwable? ->
                                logger.error(
                                    "Exception while connecting {} to initial server",
                                    player,
                                    ex
                                )
                                null
                            })
                    retire()
                }
            }
        }, mcConnection.eventLoop()).exceptionally({ ex: Throwable? ->
            logger.error("Exception while completing login initialisation phase for {}", player, ex)
            null
        })
    }

    private fun createHandler(
        server: VelocityServer?,
        inbound: LoginInboundConnection?,
        profile: GameProfile?,
        onlineMode: Boolean,
        connectedPlayer: ConnectedPlayer,
        serverIdHash: String,
    ): AuthSessionHandler {
        return NettyReflectionHelper.createAuthSessionHandler(
            server,
            inbound,
            profile,
            onlineMode,
            connectedPlayer,
            serverIdHash,
        )
    }

}