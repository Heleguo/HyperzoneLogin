package icu.h2l.api.util

import com.velocitypowered.api.util.GameProfile
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.UUID
import kotlin.random.Random

object RemapUtils {
    const val EXPECTED_NAME_PREFIX = "hzl-login-"
    const val REMAP_PREFIX = "check"
    private const val PROFILE_PREFIX = "h2l"

    fun genProfile(username: String, prefix: String): GameProfile {
        return GameProfile(
            genUUID(username, prefix), username,
            Collections.emptyList()
        )
    }

    fun genUUID(username: String, prefix: String): UUID {
        return UUID.nameUUIDFromBytes(("$prefix:$username").toByteArray(StandardCharsets.UTF_8))
    }

    fun genProfileUUID(username: String): UUID {
        val normalized = username.lowercase()
        return genUUID(normalized, PROFILE_PREFIX)
    }

    fun randomProfile(): GameProfile {
        val randomId = String.format("%06d", Random.nextInt(1_000_000))
        val newName = "$EXPECTED_NAME_PREFIX$randomId"
        return genProfile(newName, REMAP_PREFIX)
    }
}
