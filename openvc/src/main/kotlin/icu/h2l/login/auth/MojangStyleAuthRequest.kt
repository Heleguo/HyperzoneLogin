package icu.h2l.login.auth

import com.google.common.net.UrlEscapers
import com.google.gson.Gson
import com.velocitypowered.api.util.GameProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

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
     */
    private fun buildAuthUrl(username: String, serverId: String, playerIp: String?): String {
        val escapedUsername = UrlEscapers.urlFormParameterEscaper().escape(username)
        val escapedServerId = UrlEscapers.urlFormParameterEscaper().escape(serverId)
        
        val baseUrl = config.url.trimEnd('/')
        var url = "$baseUrl?username=$escapedUsername&serverId=$escapedServerId"
        
        // 如果提供了IP地址，添加到URL中
        playerIp?.let {
            val escapedIp = UrlEscapers.urlFormParameterEscaper().escape(it)
            url += "&ip=$escapedIp"
        }
        
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
                try {
                    val profile = gson.fromJson(response.body(), GameProfile::class.java)
                    AuthenticationResult.Success(profile, serverUrl)
                } catch (e: Exception) {
                    AuthenticationResult.Failure(
                        reason = "Failed to parse response: ${e.message}",
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
