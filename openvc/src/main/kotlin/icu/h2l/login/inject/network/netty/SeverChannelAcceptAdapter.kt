package icu.h2l.login.inject.network.netty

import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer

abstract class SeverChannelAcceptAdapter : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any?) {
        ctx.fireChannelRead(msg)

        msg as Channel
        msg.pipeline().addLast(object : ChannelInitializer<Channel>() {
            override fun initChannel(ch: Channel) {
                init(ch)
            }
        })
    }

    abstract fun init(channel: Channel)
}