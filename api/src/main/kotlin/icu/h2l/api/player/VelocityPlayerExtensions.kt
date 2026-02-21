package icu.h2l.api.player

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.proxy.connection.client.ConnectedPlayer

fun Player.getChannel() = (this as ConnectedPlayer).connection.channel
