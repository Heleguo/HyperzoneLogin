package icu.h2l.login.auth.online.req

import com.google.common.net.UrlEscapers
import com.google.gson.Gson
import com.velocitypowered.api.util.GameProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Mojang风格的验证服务器请求实现
 */
class MojangStyleAuthRequest(
    private val config: AuthServerConfig,
    private val httpClient: HttpClient,
    private val gson: Gson,
    private val userAgent: String = "VelocityProxy/1.0"
) : AuthenticationRequest {

    override suspend fun authenticate(
        username: String,
        serverId: String,
        playerIp: String?
    ): AuthenticationResult = withContext(Dispatchers.IO) {
        try {
            val url = buildAuthUrl(username, serverId, playerIp)
            val request = buildHttpRequest(url)
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            handleResponse(response, config.url)
        } catch (e: Exception) {
            AuthenticationResult.Failure(
                reason = "Request failed: ${e.message}",
                statusCode = null
            )
        }
    }

    /**
     * 构建验证URL
     * 使用YggdrasilAuthConfig中的URL模板，替换占位符
     */
    private fun buildAuthUrl(username: String, serverId: String, playerIp: String?): String {
        val escapedUsername = UrlEscapers.urlFormParameterEscaper().escape(username)
        val escapedServerId = UrlEscapers.urlFormParameterEscaper().escape(serverId)
        
        // 替换模板中的占位符
        var url = config.url
            .replace("{username}", escapedUsername)
            .replace("{serverId}", escapedServerId)
        
        // 处理IP占位符：如果有IP则替换为&ip=xxx，否则替换为空字符串
        val ipParam = if (playerIp != null) {
            val escapedIp = UrlEscapers.urlFormParameterEscaper().escape(playerIp)
            "&ip=$escapedIp"
        } else {
            ""
        }
        url = url.replace("{ip}", ipParam)
        
        return url
    }

    /**
     * 构建HTTP请求
     */
    private fun buildHttpRequest(url: String): HttpRequest {
        return HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(config.readTimeout)
            .header("User-Agent", userAgent)
            .GET()
            .build()
    }

    /**
     * 处理HTTP响应
     */
    private fun handleResponse(
        response: HttpResponse<String>,
        serverUrl: String
    ): AuthenticationResult {
        return when (response.statusCode()) {
            200 -> {
                val body=response.body()
                try {
                    val profile = gson.fromJson(body, GameProfile::class.java)
                    AuthenticationResult.Success(profile, serverUrl)
                } catch (e: Exception) {
                    AuthenticationResult.Failure(
                        reason = "Failed to parse response: ${e.message} body:${body}",
                        statusCode = 200
                    )
                }
            }
            204 -> {
                // 离线模式用户尝试登录在线模式代理
                AuthenticationResult.Failure(
                    reason = "Offline mode player attempted to join online mode server",
                    statusCode = 204
                )
            }
            else -> {
                AuthenticationResult.Failure(
                    reason = "Unexpected status code from authentication server",
                    statusCode = response.statusCode()
                )
            }
        }
    }
}
