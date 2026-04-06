package icu.h2l.api.event.profile

import com.velocitypowered.api.event.annotation.AwaitingEvent
import com.velocitypowered.api.util.GameProfile
import icu.h2l.api.player.HyperZonePlayer
import icu.h2l.api.profile.skin.ProfileSkinSource
import icu.h2l.api.profile.skin.ProfileSkinTextures

@AwaitingEvent
class ProfileSkinPreprocessEvent(
    val hyperZonePlayer: HyperZonePlayer,
    val authenticatedProfile: GameProfile,
    val entryId: String,
    val serverUrl: String
) {
    var source: ProfileSkinSource? = null
    var textures: ProfileSkinTextures? = null
}

