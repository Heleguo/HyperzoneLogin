package icu.h2l.login.auth

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import java.net.http.HttpClient
import java.time.Duration

/**
 * 多服务器验证使用示例
 */
class MultiServerAuthExample {

    companion object {
        /**
         * 创建默认的多服务器验证管理器
         */
        fun createDefaultManager(
            userAgent: String = "VelocityProxy/1.0",
            gson: Gson = Gson()
        ): ConcurrentAuthenticationManager {
            // 创建HTTP客户端（可复用）
            val httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()

            // 配置多个验证服务器
            val serverConfigs = listOf(
                AuthServerConfig(
                    url = "https://sessionserver.mojang.com/session/minecraft/hasJoined",
                    name = "Mojang Official",
                    connectTimeout = Duration.ofSeconds(5),
                    readTimeout = Duration.ofSeconds(10)
                ),
                AuthServerConfig(
                    url = "https://sessionserver.minecraft.net/session/minecraft/hasJoined",
                    name = "Mojang Backup",
                    connectTimeout = Duration.ofSeconds(5),
                    readTimeout = Duration.ofSeconds(10)
                ),
                // 可以添加更多的备用服务器
                // AuthServerConfig(
                //     url = "https://your-custom-auth-server.com/hasJoined",
                //     name = "Custom Server",
                //     connectTimeout = Duration.ofSeconds(3),
                //     readTimeout = Duration.ofSeconds(8)
                // )
            )

            // 创建验证请求实例
            val authRequests = serverConfigs.map { config ->
                MojangStyleAuthRequest(config, httpClient, gson, userAgent)
            }

            // 使用构建器创建并发验证管理器
            return ConcurrentAuthenticationManagerBuilder()
                .addAuthRequests(authRequests)
                .setGlobalTimeout(Duration.ofSeconds(30))
                .build()
        }

        /**
         * 示例：执行验证
         */
        fun exampleUsage() {
            val manager = createDefaultManager()

            // 在协程中执行验证
            runBlocking {
                val result = manager.authenticate(
                    username = "PlayerName",
                    serverId = "generated-server-id-here",
                    playerIp = "127.0.0.1" // 可选
                )

                when (result) {
                    is AuthenticationResult.Success -> {
                        println("验证成功!")
                        println("玩家UUID: ${result.profile.id}")
                        println("玩家名称: ${result.profile.name}")
                        println("成功的服务器: ${result.serverUrl}")
                        // 继续处理登录...
                    }
                    is AuthenticationResult.Failure -> {
                        println("验证失败: ${result.reason}")
                        result.statusCode?.let { 
                            println("状态码: $it") 
                        }
                        // 断开连接或其他处理
                    }
                    is AuthenticationResult.Timeout -> {
                        println("验证超时")
                        println("尝试的服务器: ${result.attemptedServers.joinToString()}")
                        // 断开连接
                    }
                }
            }
        }

        /**
         * 在Velocity环境中使用的示例
         */
        suspend fun authenticatePlayer(
            manager: ConcurrentAuthenticationManager,
            username: String,
            serverId: String,
            playerIp: String?,
            preventProxyConnections: Boolean = false
        ): AuthenticationResult {
            return manager.authenticate(
                username = username,
                serverId = serverId,
                playerIp = if (preventProxyConnections) playerIp else null
            )
        }
    }
}
