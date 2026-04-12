/*
 * This file is part of HyperZoneLogin, licensed under the GNU Affero General Public License v3.0 or later.
 *
 * Copyright (C) ksqeib (庆灵) <ksqeib@qq.com>
 * Copyright (C) contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package icu.h2l.login.profile.skin.service

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.util.GameProfile
import icu.h2l.api.event.profile.ProfileSkinApplyEvent
import icu.h2l.api.event.profile.ProfileSkinPreprocessEvent
import icu.h2l.api.log.debug
import icu.h2l.api.log.error
import icu.h2l.api.log.warn
import icu.h2l.api.profile.skin.ProfileSkinModel
import icu.h2l.api.profile.skin.ProfileSkinSource
import icu.h2l.api.profile.skin.ProfileSkinTextures
import icu.h2l.login.profile.skin.config.MineSkinMethod
import icu.h2l.login.profile.skin.config.ProfileSkinConfig
import icu.h2l.login.profile.skin.db.ProfileSkinCacheRepository
import icu.h2l.login.profile.skin.db.ProfileSkinCacheRepository.SaveResult
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO
import net.kyori.adventure.text.Component

private class MineSkinRequestFailedException(
    val method: MineSkinMethod,
    val statusCode: Int,
    val body: String
) : IllegalStateException("MineSkin ${method.name} restore failed: HTTP $statusCode, body=$body")

internal fun shouldRetryUploadAfterUrlReadFailure(statusCode: Int, body: String): Boolean {
    if (statusCode != 400) {
        return false
    }

    val root = runCatching {
        JsonParser.parseString(body).asJsonObject
    }.getOrNull() ?: return body.contains("Invalid image file size: undefined", ignoreCase = true)

    val errorCode = root.getAsJsonPrimitive("errorCode")?.asString
    val error = root.getAsJsonPrimitive("error")?.asString
    return errorCode.equals("invalid_image", ignoreCase = true)
            && error?.contains("Invalid image file size: undefined", ignoreCase = true) == true
}

@Suppress("UNUSED_PARAMETER")
internal fun shouldUseSourceCache(shouldForceRestoreSignedTextures: Boolean): Boolean {
    /**
     * 现在 source cache 是否可复用由数据库中的 `source_cache_eligible` 明确控制，
     * 因此即使是“上游 signed 但不可信，需要强制修复”的场景，也应该先尝试命中同源已恢复缓存，
     * 避免对 MineSkin 重复请求并触发 429。
     */
    if (shouldForceRestoreSignedTextures) {
        return true
    }
    return true
}

internal fun sanitizeFallbackTextures(
    textures: ProfileSkinTextures,
    shouldForceRestoreSignedTextures: Boolean
): ProfileSkinTextures {
    /**
     * 当上游 signed textures 不可信且 MineSkin 修复失败时，不能再构造“value 存在但 signature 为空”的半残属性，
     * 因为 Velocity 的 `GameProfile.Property` 不接受空签名。
     *
     * 这里采用折中策略：保留最开始传入的整份 signed textures 作为 profile 级 fallback，
     * 同时配合 `sanitizeFallbackSourceHash` 禁止把这份不可信结果继续提升为 source 级缓存。
     */
    return textures
}

internal fun sanitizeFallbackSourceHash(
    sourceHash: String?,
    shouldForceRestoreSignedTextures: Boolean
): String? {
    return if (shouldForceRestoreSignedTextures) null else sourceHash
}

class ProfileSkinService(
    private val config: ProfileSkinConfig,
    private val repository: ProfileSkinCacheRepository
) {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(config.mineSkin.timeoutMillis))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    @Subscribe
    fun onPreprocess(event: ProfileSkinPreprocessEvent) {
        if (!config.enabled) return

        val profileId = event.hyperZonePlayer.getDBProfile()?.id ?: run {
            debug {
                "[ProfileSkinFlow] preprocess skip: no DB profile, player=${event.hyperZonePlayer.userName}, entry=${event.entryId}"
            }
            return
        }
        val upstreamTextures = event.textures ?: extractTextures(event.authenticatedProfile)
        val source = (event.source ?: extractSkinSource(upstreamTextures))?.normalized()
        val sourceHash = source?.let(::sourceHash)
        val trustedSignedEntry = isTrustedSignedEntry(event.entryId)
        val shouldTrustSignedTextures = upstreamTextures?.isSigned == true
                && config.preferUpstreamSignedTextures
                && trustedSignedEntry
        val shouldForceRestoreSignedTextures = upstreamTextures?.isSigned == true && !trustedSignedEntry
        val shouldAttemptRestore = source != null && (shouldForceRestoreSignedTextures || config.restoreUnsignedTextures)

        if (shouldTrustSignedTextures) {
            logSaveResult(
                repository.save(
                    profileId = profileId,
                    source = source,
                    textures = upstreamTextures,
                    sourceHash = sourceHash,
                    sourceCacheEligible = true
                ),
                profileId,
                source,
                sourceHash,
                "trusted signed upstream"
            )
            event.textures = upstreamTextures
            return
        }

        if (upstreamTextures?.isSigned == true && !trustedSignedEntry) {
            debug {
                "[ProfileSkinFlow] preprocess signed upstream not trusted: profile=$profileId, entry=${event.entryId}, source=${describeSource(source)}"
            }
        }

        if (shouldAttemptRestore) {
            if (shouldUseSourceCache(shouldForceRestoreSignedTextures)) {
            repository.findBySourceHash(sourceHash!!)?.let { cached ->
                logSaveResult(
                    repository.save(
                        profileId = profileId,
                        source = source,
                        textures = cached.textures,
                        sourceHash = sourceHash,
                        sourceCacheEligible = true
                    ),
                    profileId,
                    source,
                    sourceHash,
                    "source cache hit"
                )
                event.textures = cached.textures
                return
            }
            }

            runCatching {
                restoreTextures(source)
            }.onSuccess { restored ->
                logSaveResult(
                    repository.save(
                        profileId = profileId,
                        source = source,
                        textures = restored,
                        sourceHash = sourceHash,
                        sourceCacheEligible = true
                    ),
                    profileId,
                    source,
                    sourceHash,
                    "restored textures"
                )
                event.textures = restored
                return
            }.onFailure { throwable ->
                error(throwable) { "Profile skin restore failed for profile=$profileId: ${throwable.message}" }
            }
        }

        if (upstreamTextures != null) {
            val fallbackTextures = sanitizeFallbackTextures(upstreamTextures, shouldForceRestoreSignedTextures)
            val fallbackSourceHash = sanitizeFallbackSourceHash(sourceHash, shouldForceRestoreSignedTextures)
            if (shouldForceRestoreSignedTextures && fallbackTextures.isSigned) {
                warn {
                    "[ProfileSkinFlow] preprocess fallback uses original untrusted signed textures after restore failure: profile=$profileId, entry=${event.entryId}, source=${describeSource(source)}, sourceHashCacheDisabled=${fallbackSourceHash == null}"
                }
            }
            logSaveResult(
                repository.save(
                    profileId = profileId,
                    source = source,
                    textures = fallbackTextures,
                    sourceHash = fallbackSourceHash,
                    sourceCacheEligible = false
                ),
                profileId,
                source,
                fallbackSourceHash,
                "upstream fallback"
            )
            event.textures = fallbackTextures
        } else {
            debug {
                "[ProfileSkinFlow] preprocess finished without textures: profile=$profileId, source=${describeSource(source)}"
            }
        }
    }

    @Subscribe
    fun onApply(event: ProfileSkinApplyEvent) {
        if (!config.enabled) return

        val profileId = event.hyperZonePlayer.getDBProfile()?.id ?: run {
            debug {
                "[ProfileSkinFlow] apply listener failed: no attached DB profile, player=${event.hyperZonePlayer.userName}, base=${describeProfile(event.baseProfile)}"
            }
            event.hyperZonePlayer.sendMessage(Component.text("§c皮肤修复失败，需要重新进入游戏"))
            return
        }
        repository.findByProfileId(profileId)?.let { cached ->
            event.textures = cached.textures
            return
        }
    }

    private fun restoreTextures(source: ProfileSkinSource): ProfileSkinTextures {
        val body = when (MineSkinMethod.from(config.mineSkin.method)) {
            MineSkinMethod.URL -> restoreByUrlWithUploadRetry(source)
            MineSkinMethod.UPLOAD -> restoreByUpload(source)
        }
        return parseMineSkinResponse(body)
    }

    private fun restoreByUrlWithUploadRetry(source: ProfileSkinSource): String {
        return runCatching {
            restoreByUrl(source)
        }.recoverCatching { throwable ->
            val failure = throwable as? MineSkinRequestFailedException
            if (failure?.method != MineSkinMethod.URL
                || !config.mineSkin.retryUploadOnUrlReadFailure
                || !shouldRetryUploadAfterUrlReadFailure(failure.statusCode, failure.body)
            ) {
                throw throwable
            }

            warn {
                "[ProfileSkinFlow] MineSkin URL restore hit remote-read failure, retrying with upload: source=${describeSource(source)}, status=${failure.statusCode}, errorBody=${failure.body}"
            }
            restoreByUpload(source)
        }.getOrThrow()
    }

    private fun restoreByUrl(source: ProfileSkinSource): String {
        val payload = JsonObject().apply {
            addProperty("name", UUID.randomUUID().toString().substring(0, 6))
            addProperty("variant", source.model)
            addProperty("visibility", 0)
            addProperty("url", source.skinUrl)
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(config.mineSkin.urlEndpoint))
            .timeout(Duration.ofMillis(config.mineSkin.timeoutMillis))
            .header("User-Agent", config.mineSkin.userAgent)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw MineSkinRequestFailedException(MineSkinMethod.URL, response.statusCode(), response.body())
        }
        return response.body()
    }

    private fun restoreByUpload(source: ProfileSkinSource): String {
        val bytes = requireValidSkin(source.skinUrl)
        val boundary = "----HyperZoneLogin${UUID.randomUUID().toString().replace("-", "")}"
        val separator = "--$boundary\r\n"
        val end = "--$boundary--\r\n"
        val body = ByteArrayOutputStream().use { output ->
            output.write(separator.toByteArray(StandardCharsets.UTF_8))
            output.write("Content-Disposition: form-data; name=\"name\"\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
            output.write(UUID.randomUUID().toString().substring(0, 6).toByteArray(StandardCharsets.UTF_8))
            output.write("\r\n".toByteArray(StandardCharsets.UTF_8))

            output.write(separator.toByteArray(StandardCharsets.UTF_8))
            output.write("Content-Disposition: form-data; name=\"variant\"\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
            output.write(source.model.toByteArray(StandardCharsets.UTF_8))
            output.write("\r\n".toByteArray(StandardCharsets.UTF_8))

            output.write(separator.toByteArray(StandardCharsets.UTF_8))
            output.write("Content-Disposition: form-data; name=\"visibility\"\r\n\r\n0\r\n".toByteArray(StandardCharsets.UTF_8))

            output.write(separator.toByteArray(StandardCharsets.UTF_8))
            output.write("Content-Disposition: form-data; name=\"file\"; filename=\"upload.png\"\r\n".toByteArray(StandardCharsets.UTF_8))
            output.write("Content-Type: image/png\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
            output.write(bytes)
            output.write("\r\n".toByteArray(StandardCharsets.UTF_8))
            output.write(end.toByteArray(StandardCharsets.UTF_8))
            output.toByteArray()
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(config.mineSkin.uploadEndpoint))
            .timeout(Duration.ofMillis(config.mineSkin.timeoutMillis))
            .header("User-Agent", config.mineSkin.userAgent)
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw MineSkinRequestFailedException(MineSkinMethod.UPLOAD, response.statusCode(), response.body())
        }
        return response.body()
    }

    private fun parseMineSkinResponse(body: String): ProfileSkinTextures {
        val root = JsonParser.parseString(body).asJsonObject
        val texture = root.getAsJsonObject("data")
            ?.getAsJsonObject("texture")
            ?: throw IllegalStateException("MineSkin response missing data.texture: $body")

        val value = texture.getAsJsonPrimitive("value")?.asString
            ?: throw IllegalStateException("MineSkin response missing value: $body")
        val signature = texture.getAsJsonPrimitive("signature")?.asString
        return ProfileSkinTextures(value = value, signature = signature)
    }

    private fun requireValidSkin(skinUrl: String): ByteArray {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(skinUrl))
            .timeout(Duration.ofMillis(config.mineSkin.timeoutMillis))
            .header("User-Agent", config.mineSkin.userAgent)
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Skin download failed: HTTP ${response.statusCode()}")
        }

        val bytes = response.body()
        ByteArrayInputStream(bytes).use { input ->
            val image: BufferedImage = ImageIO.read(input)
                ?: throw IllegalStateException("Skin image decode failed")
            if (image.width != 64) {
                throw IllegalStateException("Skin width is not 64")
            }
            if (image.height != 32 && image.height != 64) {
                throw IllegalStateException("Skin height is not 64 or 32")
            }
        }
        return bytes
    }

    private fun extractTextures(profile: GameProfile?): ProfileSkinTextures? {
        val property = profile?.properties?.firstOrNull { it.name.equals("textures", ignoreCase = true) } ?: return null
        return ProfileSkinTextures(property.value, property.signature)
    }

    private fun extractSkinSource(textures: ProfileSkinTextures?): ProfileSkinSource? {
        val value = textures?.value ?: return null
        val decoded = String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
        val root = JsonParser.parseString(decoded).asJsonObject
        val skin = root.getAsJsonObject("textures")
            ?.getAsJsonObject("SKIN")
            ?: return null
        val url = skin.getAsJsonPrimitive("url")?.asString ?: return null
        val model = skin.getAsJsonObject("metadata")
            ?.getAsJsonPrimitive("model")
            ?.asString
        return ProfileSkinSource(url, ProfileSkinModel.normalize(model))
    }

    private fun sourceHash(source: ProfileSkinSource): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val value = "${source.skinUrl}|${source.model}".toByteArray(StandardCharsets.UTF_8)
        return digest.digest(value).joinToString("") { "%02x".format(it) }
    }

    private fun describeProfile(profile: GameProfile?): String {
        if (profile == null) {
            return "null"
        }
        return "id=${profile.id}, name=${profile.name}, properties=${profile.properties.size}, textures=${describeTextures(extractTextures(profile))}"
    }

    private fun describeTextures(textures: ProfileSkinTextures?): String {
        if (textures == null) {
            return "none"
        }
        return "present(valueLength=${textures.value.length}, signed=${textures.isSigned})"
    }

    private fun describeSource(source: ProfileSkinSource?): String {
        if (source == null) {
            return "none"
        }
        return "url=${source.skinUrl}, model=${source.model}"
    }

    private fun shortHash(value: String?): String {
        if (value.isNullOrBlank()) {
            return "none"
        }
        return value.take(12)
    }

    private fun isTrustedSignedEntry(entryId: String): Boolean {
        return config.trustedSignedTextureEntries.any { it.equals(entryId, ignoreCase = true) }
    }

    private fun describeRestoreSkipReason(
        source: ProfileSkinSource?,
        shouldForceRestoreSignedTextures: Boolean
    ): String {
        if (source == null) {
            return if (shouldForceRestoreSignedTextures) {
                "missing source for untrusted signed textures"
            } else {
                "missing source"
            }
        }
        return if (shouldForceRestoreSignedTextures) {
            "untrusted signed textures without restore path"
        } else {
            "restoreUnsignedTextures disabled"
        }
    }

    private fun logSaveResult(
        result: SaveResult,
        profileId: UUID,
        source: ProfileSkinSource?,
        sourceHash: String?,
        reason: String
    ) = Unit
}


