package icu.h2l.api.event.profile

import com.velocitypowered.api.event.annotation.AwaitingEvent
import com.velocitypowered.api.util.GameProfile
import icu.h2l.api.player.HyperZonePlayer
import icu.h2l.api.profile.skin.ProfileSkinTextures

@AwaitingEvent
class ProfileSkinApplyEvent(
    val hyperZonePlayer: HyperZonePlayer,
    val baseProfile: GameProfile
) {
    var textures: ProfileSkinTextures? = null
}

