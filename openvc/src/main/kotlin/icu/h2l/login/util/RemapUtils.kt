package icu.h2l.login.util

import com.google.common.collect.ImmutableList
import com.velocitypowered.api.util.GameProfile
import java.nio.charset.StandardCharsets
import java.util.*

object RemapUtils {
    fun genProfile(username: String, prefix: String): GameProfile {
        return GameProfile(
            genUUID(username, prefix), username,
            ImmutableList.of<GameProfile.Property?>()
        )
    }

    fun genUUID(username: String, prefix: String): UUID {
        return UUID.nameUUIDFromBytes(("$prefix:$username").toByteArray(StandardCharsets.UTF_8))
    }
}