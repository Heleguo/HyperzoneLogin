package icu.h2l.login.inject.network.netty

import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import java.lang.reflect.Method


//https://github.com/ViaVersion/ViaVersion/blob/master/common/src/main/java/com/viaversion/viaversion/platform/ViaChannelInitializer.java
abstract class ViaChannelInitializer(
    private val original: ChannelInitializer<Channel>,
) : ChannelInitializer<Channel>() {

    private companion object {
        private val INIT_CHANNEL_METHOD: Method =
            ChannelInitializer::class.java.getDeclaredMethod("initChannel", Channel::class.java)
                .also { it.isAccessible = true }

    }

    override fun initChannel(ch: Channel) {
        INIT_CHANNEL_METHOD.invoke(original, ch)

        injectChannel(ch)
    }

    protected abstract fun injectChannel(channel: Channel)
}