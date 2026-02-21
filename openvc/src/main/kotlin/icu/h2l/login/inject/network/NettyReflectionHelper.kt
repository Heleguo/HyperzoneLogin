package icu.h2l.login.inject.network

import com.velocitypowered.api.network.HandshakeIntent
import com.velocitypowered.api.permission.PermissionFunction
import com.velocitypowered.api.permission.PermissionProvider
import com.velocitypowered.api.proxy.crypto.IdentifiedKey
import com.velocitypowered.api.util.GameProfile
import com.velocitypowered.proxy.VelocityServer
import com.velocitypowered.proxy.connection.MinecraftConnection
import com.velocitypowered.proxy.connection.client.AuthSessionHandler
import com.velocitypowered.proxy.connection.client.ConnectedPlayer
import com.velocitypowered.proxy.connection.client.InitialConnectSessionHandler
import com.velocitypowered.proxy.connection.client.LoginInboundConnection
import icu.h2l.login.HyperZoneLoginMain
import java.net.InetSocketAddress

private fun interface AuthSessionHandlerConstructor {
    fun create(
        server: VelocityServer?,
        inbound: LoginInboundConnection?,
        profile: GameProfile?,
        onlineMode: Boolean,
        serverIdHash: String,
    ): AuthSessionHandler
}

private fun interface ConnectedPlayerConstructor {
    fun create(
        server: VelocityServer,
        profile: GameProfile,
        mcConnection: MinecraftConnection,
        virtualHost: InetSocketAddress?,
        rawVirtualHost: String?,
        onlineMode: Boolean,
        handshakeIntent: HandshakeIntent,
        identifiedKey: IdentifiedKey?,
    ): ConnectedPlayer
}

@Suppress("ObjectPrivatePropertyName")
object NettyReflectionHelper {

    private val `ConnectedPlayer$DEFAULT_PERMISSIONS` by lazy {
        ConnectedPlayer::class.java.getDeclaredField("DEFAULT_PERMISSIONS").also { it.isAccessible = true }
    }

    private val `ConnectedPlayer$setPermissionFunction` by lazy {
        ConnectedPlayer::class.java.getDeclaredMethod("setPermissionFunction", PermissionFunction::class.java)
            .also { it.isAccessible = true }
    }

    private val `InitialConnectSessionHandler$init` by lazy {
        InitialConnectSessionHandler::class.java.getDeclaredConstructor(
            ConnectedPlayer::class.java,
            VelocityServer::class.java,
        ).also { it.isAccessible = true }
    }

    private val `LoginInboundConnection$fireLogin` by lazy {
        LoginInboundConnection::class.java.getDeclaredMethod("loginEventFired", Runnable::class.java)
            .also { it.isAccessible = true }
    }

    fun LoginInboundConnection.fireLogin(action: Runnable) {
        `LoginInboundConnection$fireLogin`.invoke(this@fireLogin, action)
    }

    fun getDefaultPermissionsProvider(): PermissionProvider {
        return `ConnectedPlayer$DEFAULT_PERMISSIONS`.get(null) as PermissionProvider
    }

    fun setPermissionFunction(player: ConnectedPlayer, function: PermissionFunction) {
        `ConnectedPlayer$setPermissionFunction`.invoke(player, function)
    }

    fun createInitialConnectSessionHandler(player: ConnectedPlayer, server: VelocityServer): InitialConnectSessionHandler {
        return `InitialConnectSessionHandler$init`.newInstance(player, server)
    }

    private val `AuthSessionHandler$init`: AuthSessionHandlerConstructor by lazy {
        runCatching {
            val ctor = AuthSessionHandler::class.java.getDeclaredConstructor(
                VelocityServer::class.java,
                LoginInboundConnection::class.java,
                GameProfile::class.java,
                Boolean::class.javaPrimitiveType,
            ).also { it.isAccessible = true }

            AuthSessionHandlerConstructor { server: VelocityServer?,
                                            inbound: LoginInboundConnection?,
                                            profile: GameProfile?,
                                            onlineMode: Boolean,
                                            serverIdHash: String ->
                ctor.newInstance(server, inbound, profile, onlineMode)
            }
        }.recoverCatching {
            val ctor = AuthSessionHandler::class.java.getDeclaredConstructor(
                VelocityServer::class.java,
                LoginInboundConnection::class.java,
                GameProfile::class.java,
                Boolean::class.javaPrimitiveType,
                String::class.java,
            ).also { it.isAccessible = true }

            AuthSessionHandlerConstructor { server: VelocityServer?,
                                            inbound: LoginInboundConnection?,
                                            profile: GameProfile?,
                                            onlineMode: Boolean,
                                            serverIdHash: String ->
                ctor.newInstance(server, inbound, profile, onlineMode, serverIdHash)
            }
        }.getOrThrow()
    }

    private val `ConnectedPlayer$init`: ConnectedPlayerConstructor by lazy {
        val ctor = ConnectedPlayer::class.java.getDeclaredConstructor(
            VelocityServer::class.java,
            GameProfile::class.java,
            MinecraftConnection::class.java,
            InetSocketAddress::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType,
            HandshakeIntent::class.java,
            IdentifiedKey::class.java,
        ).also { it.isAccessible = true }

        ConnectedPlayerConstructor { server,
                                     profile,
                                     mcConnection,
                                     virtualHost,
                                     rawVirtualHost,
                                     onlineMode,
                                     handshakeIntent,
                                     identifiedKey ->
            ctor.newInstance(
                server,
                profile,
                mcConnection,
                virtualHost,
                rawVirtualHost,
                onlineMode,
                handshakeIntent,
                identifiedKey,
            )
        }
    }

    fun createAuthSessionHandler(
        server: VelocityServer?,
        inbound: LoginInboundConnection?,
        profile: GameProfile?,
        onlineMode: Boolean,
        serverIdHash: String,
    ): AuthSessionHandler {
        return runCatching {
            `AuthSessionHandler$init`.create(server, inbound, profile, onlineMode, serverIdHash)
        }.getOrElse { reflectionException ->
            HyperZoneLoginMain.getInstance().logger.error(
                "反射创建 AuthSessionHandler 失败。",
                reflectionException
            )
            throw reflectionException
        }
    }
}
