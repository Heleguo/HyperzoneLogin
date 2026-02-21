package icu.h2l.login.inject.network

import com.velocitypowered.api.util.GameProfile
import com.velocitypowered.proxy.VelocityServer
import com.velocitypowered.proxy.connection.client.AuthSessionHandler
import com.velocitypowered.proxy.connection.client.LoginInboundConnection
import com.velocitypowered.proxy.connection.client.NettyAuthSessionHandler


private fun interface AuthSessionHandlerConstructor {
    fun create(
        server: VelocityServer?,
        inbound: LoginInboundConnection?,
        profile: GameProfile?,
        onlineMode: Boolean,
        serverIdHash: String,
    ): AuthSessionHandler
}

@Suppress("ObjectPrivatePropertyName")
object NettyReflectionHelper {

    private val `LoginInboundConnection$fireLogin` by lazy {
        LoginInboundConnection::class.java.getDeclaredMethod("loginEventFired", Runnable::class.java)
            .also { it.isAccessible = true }
    }

    fun LoginInboundConnection.fireLogin(action: Runnable) {
        `LoginInboundConnection$fireLogin`.invoke(this@fireLogin, action)
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
        }.recoverCatching { err ->
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

    fun createAuthSessionHandler(
        server: VelocityServer?,
        inbound: LoginInboundConnection?,
        profile: GameProfile?,
        onlineMode: Boolean,
        serverIdHash: String,
    ): AuthSessionHandler {
            return runCatching {
                NettyAuthSessionHandler(
                    requireNotNull(server) { "server" },
                    requireNotNull(inbound) { "inbound" },
                    requireNotNull(profile) { "profile" },
                    onlineMode,
                )
            }.getOrElse {
                `AuthSessionHandler$init`.create(server, inbound, profile, onlineMode, serverIdHash)
            }
    }
}