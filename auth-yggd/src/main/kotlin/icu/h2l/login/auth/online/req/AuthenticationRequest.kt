package icu.h2l.login.auth.online.req

import java.time.Duration

/**
 * 验证服务器配置
 */
data class AuthServerConfig(
    val url: String,
    val name: String,
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val readTimeout: Duration = Duration.ofSeconds(10)
)

/**
 * 验证请求接口
 */
interface AuthenticationRequest {
    /**
     * 执行验证请求
     * @param username 玩家用户名
     * @param serverId 服务器ID（由shared secret和公钥生成）
     * @param playerIp 玩家IP地址（可选）
     * @return 验证结果
     */
    suspend fun authenticate(
        username: String,
        serverId: String,
        playerIp: String? = null
    ): AuthenticationResult
}
