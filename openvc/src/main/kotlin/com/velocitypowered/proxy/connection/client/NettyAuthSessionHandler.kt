package com.velocitypowered.proxy.connection.client

import com.velocitypowered.api.event.permission.PermissionsSetupEvent
import com.velocitypowered.api.event.player.GameProfileRequestEvent
import com.velocitypowered.api.permission.PermissionFunction
import com.velocitypowered.api.util.GameProfile
import com.velocitypowered.proxy.VelocityServer
import com.velocitypowered.proxy.connection.MinecraftConnection
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.CompletableFuture

class NettyAuthSessionHandler(
    server: VelocityServer,
    inbound: LoginInboundConnection,
    profile: GameProfile,
    onlineMode: Boolean,
) : AuthSessionHandler(server, inbound, profile, onlineMode) {

    companion object {
        private val logger: Logger = LogManager.getLogger(AuthSessionHandler::class.java)
    }

    private object Ref {
        val server: Field = field("server")
        val mcConnection: Field = field("mcConnection")
        val inbound: Field = field("inbound")
        val profile: Field = field("profile")
        val connectedPlayer: Field = field("connectedPlayer")
        val onlineMode: Field = field("onlineMode")
        val startLoginCompletion: Method = AuthSessionHandler::class.java.getDeclaredMethod(
            "startLoginCompletion",
            ConnectedPlayer::class.java,
        ).also { it.isAccessible = true }

        private fun field(name: String): Field {
            return AuthSessionHandler::class.java.getDeclaredField(name).also { it.isAccessible = true }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getField(field: Field): T = field.get(this) as T

    private fun setField(field: Field, value: Any?) {
        field.set(this, value)
    }

    override fun activated() {
        val server = getField<VelocityServer>(Ref.server)
        val mcConnection = getField<MinecraftConnection>(Ref.mcConnection)
        val inbound = getField<LoginInboundConnection>(Ref.inbound)
        var profile = getField<GameProfile>(Ref.profile)
        val onlineMode = Ref.onlineMode.getBoolean(this)

        profile = mcConnection.type.addGameProfileTokensIfRequired(
            profile,
            server.configuration.playerInfoForwardingMode,
        )
        setField(Ref.profile, profile)

        val profileRequestEvent = GameProfileRequestEvent(inbound, profile, onlineMode)
        val finalProfile = profile

        server.eventManager.fire(profileRequestEvent).thenComposeAsync({ profileEvent ->
            if (mcConnection.isClosed) {
                return@thenComposeAsync CompletableFuture.completedFuture<Void?>(null)
            }

            val player = ConnectedPlayer(
                server,
                profileEvent.gameProfile,
                mcConnection,
                inbound.virtualHost.orElse(null),
                inbound.rawVirtualHost.orElse(null),
                onlineMode,
                inbound.handshakeIntent,
                inbound.identifiedKey,
            )
            setField(Ref.connectedPlayer, player)

            if (!server.canRegisterConnection(player)) {
                player.disconnect0(
                    Component.translatable("velocity.error.already-connected-proxy", NamedTextColor.RED),
                    true,
                )
                return@thenComposeAsync CompletableFuture.completedFuture<Void?>(null)
            }

            if (server.configuration.isLogPlayerConnections) {
                logger.info("{} has connected", player)
            }

            server.eventManager
                .fire(PermissionsSetupEvent(player, ConnectedPlayer.DEFAULT_PERMISSIONS))
                .thenAcceptAsync({ event ->
                    if (!mcConnection.isClosed) {
                        val function: PermissionFunction? = event.createFunction(player)
                        if (function == null) {
                            logger.error(
                                "A plugin permission provider {} provided an invalid permission function for player {}. " +
                                    "This is a bug in the plugin, not in Velocity. Falling back to the default permission function.",
                                event.provider.javaClass.name,
                                player.username,
                            )
                        } else {
                            player.setPermissionFunction(function)
                        }
                        Ref.startLoginCompletion.invoke(this, player)
                    }
                }, mcConnection.eventLoop())
        }, mcConnection.eventLoop()).exceptionally { ex ->
            logger.error("Exception during connection of {}", finalProfile, ex)
            null
        }
    }
}
