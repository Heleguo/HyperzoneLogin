package icu.h2l.api.event.limbo

import com.velocitypowered.api.proxy.Player
import icu.h2l.api.limbo.handler.LimboAuthSessionOverVerify
import net.elytrium.limboapi.api.player.LimboPlayer

class LimboSpawnEvent(
    val limboPlayer: LimboPlayer,
    val proxyPlayer: Player,
    val sessionOverVerify: LimboAuthSessionOverVerify
)
