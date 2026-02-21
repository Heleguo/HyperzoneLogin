package icu.h2l.login.inject.network.netty

import com.velocitypowered.proxy.connection.MinecraftConnection
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection
import com.velocitypowered.proxy.connection.client.ConnectedPlayer
import com.velocitypowered.proxy.protocol.packet.HandshakePacket
import com.velocitypowered.proxy.protocol.packet.LoginPluginResponsePacket
import com.velocitypowered.proxy.protocol.packet.ServerLoginPacket
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise

class ToBackendPacketReplacer : ChannelOutboundHandlerAdapter() {
    private lateinit var mcConnection: MinecraftConnection
    private lateinit var velocityServerConnection: VelocityServerConnection
    private lateinit var player: ConnectedPlayer

    private fun replaceMessage(
        channel: Channel,
        ctx: ChannelHandlerContext,
        msg: Any?
    ) = when (msg) {
        is HandshakePacket -> {
            msg
        }
        is ServerLoginPacket -> {
            msg
        }
        is LoginPluginResponsePacket -> {
//  com.velocitypowered.proxy.connection.backend.LoginSessionHandler.handle(com.velocitypowered.proxy.protocol.packet.LoginPluginMessagePacket)

            msg
        }

        else -> msg
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any?, promise: ChannelPromise?) {
        initFields(ctx)

//        println("W: $msg")

        super.write(ctx, replaceMessage(ctx.channel(), ctx, msg), promise)
    }

    private fun initFields(ctx: ChannelHandlerContext) {
        if (::mcConnection.isInitialized) {
            return
        }
        val conn = ctx.channel().pipeline().get(MinecraftConnection::class.java) ?: return

        this.mcConnection = conn
        this.velocityServerConnection = conn.association as VelocityServerConnection
        this.player = velocityServerConnection.player
    }
}