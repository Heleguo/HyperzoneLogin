package icu.h2l.login.auth

import com.velocitypowered.api.util.GameProfile
import java.util.UUID

/**
 * 验证结果的封装类
 */
sealed class AuthenticationResult {
    /**
     * 验证成功
     * @param profile 玩家的游戏档案
     * @param serverUrl 成功验证的服务器URL
     */
    data class Success(
        val profile: GameProfile,
        val serverUrl: String
    ) : AuthenticationResult()

    /**
     * 验证失败
     * @param reason 失败原因
     * @param statusCode HTTP状态码（如果有）
     */
    data class Failure(
        val reason: String,
        val statusCode: Int? = null
    ) : AuthenticationResult()

    /**
     * 验证超时
     * @param attemptedServers 尝试的服务器列表
     */
    data class Timeout(
        val attemptedServers: List<String>
    ) : AuthenticationResult()

    /**
     * 判断是否为成功结果
     */
    fun isSuccess(): Boolean = this is Success

    /**
     * 获取成功结果，如果不是成功则返回null
     */
    fun getSuccessOrNull(): Success? = this as? Success
}
