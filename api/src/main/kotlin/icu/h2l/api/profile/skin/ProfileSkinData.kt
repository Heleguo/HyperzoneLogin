package icu.h2l.api.profile.skin

import com.velocitypowered.api.util.GameProfile

object ProfileSkinModel {
    const val CLASSIC = "classic"
    const val SLIM = "slim"

    fun normalize(model: String?): String {
        return if (model.equals(SLIM, ignoreCase = true)) SLIM else CLASSIC
    }
}

data class ProfileSkinSource(
    val skinUrl: String,
    val model: String = ProfileSkinModel.CLASSIC
) {
    fun normalized(): ProfileSkinSource {
        return copy(model = ProfileSkinModel.normalize(model))
    }
}

data class ProfileSkinTextures(
    val value: String,
    val signature: String? = null
) {
    fun toProperty(): GameProfile.Property {
        return GameProfile.Property("textures", value, signature)
    }

    val isSigned: Boolean
        get() = !signature.isNullOrBlank()
}

