package icu.h2l.login.auth.online.req

import icu.h2l.login.util.debug
import icu.h2l.login.util.info
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 并发验证管理器
 * 支持向多个验证服务器并发发送请求，任意一个成功即返回
 */
class ConcurrentAuthenticationManager(
    private val authRequests: List<AuthenticationRequest>,
    private val globalTimeout: Duration = Duration.ofSeconds(30)
) {
    
    /**
     * 执行并发验证
     * @param username 玩家用户名
     * @param serverId 服务器ID
     * @param playerIp 玩家IP（可选）
     * @return 验证结果
     */
    suspend fun authenticate(
        username: String,
        serverId: String,
        playerIp: String? = null
    ): AuthenticationResult = coroutineScope {
        if (authRequests.isEmpty()) {
            return@coroutineScope AuthenticationResult.Failure(
                reason = "No authentication servers configured"
            )
        }

        debug { "Starting concurrent authentication for user: $username with ${authRequests.size} servers" }

        // 用于标记是否已经有成功的结果
        val completed = AtomicBoolean(false)
        val mutex = Mutex()
        var successResult: AuthenticationResult.Success? = null
        val failures = mutableListOf<AuthenticationResult.Failure>()

        try {
            // 设置全局超时
            withTimeout(globalTimeout.toMillis()) {
                // 为每个验证请求创建一个协程
                val jobs = authRequests.mapIndexed { index, authRequest ->
                    async(Dispatchers.IO) {
                        if (completed.get()) {
                            debug { "Server $index: Skipping - another server already succeeded" }
                            return@async null
                        }

                        debug { "Server $index: Starting authentication request" }
                        val result = try {
                            authRequest.authenticate(username, serverId, playerIp)
                        } catch (e: Exception) {
                            debug { "Server $index: Exception occurred - ${e.message}" }
                            AuthenticationResult.Failure(
                                reason = "Exception: ${e.message}",
                                statusCode = null
                            )
                        }

                        // 处理结果
                        when (result) {
                            is AuthenticationResult.Success -> {
                                // 检查是否是第一个成功的
                                if (completed.compareAndSet(false, true)) {
                                    mutex.withLock {
                                        successResult = result
                                    }
                                    info { "Server $index: Authentication succeeded for $username" }
                                    result
                                } else {
                                    debug { "Server $index: Succeeded but another server was faster" }
                                    null
                                }
                            }
                            is AuthenticationResult.Failure -> {
                                debug { "Server $index: Authentication failed - ${result.reason}" }
                                mutex.withLock {
                                    failures.add(result)
                                }
                                null
                            }
                            is AuthenticationResult.Timeout -> {
                                debug { "Server $index: Authentication timed out" }
                                null
                            }
                        }
                    }
                }

                // 等待所有任务完成或第一个成功
                while (jobs.any { !it.isCompleted } && !completed.get()) {
                    delay(10) // 短暂延迟避免忙等待
                }

                // 如果有成功结果，取消其他所有任务
                if (completed.get()) {
                    jobs.forEach { job ->
                        if (!job.isCompleted) {
                            job.cancel()
                        }
                    }
                }

                // 等待所有任务完成清理
                jobs.forEach { it.cancelAndJoin() }
            }
        } catch (e: TimeoutCancellationException) {
            info { "Authentication timed out for user: $username after ${globalTimeout.toMillis()}ms" }
            return@coroutineScope AuthenticationResult.Timeout(
                attemptedServers = authRequests.map { it.toString() }
            )
        }

        // 返回结果
        successResult?.let {
            debug { "Returning success result for $username" }
            return@coroutineScope it
        }

        // 如果没有成功结果，返回综合的失败信息
        info { "All authentication servers failed for user: $username" }
        return@coroutineScope AuthenticationResult.Failure(
            reason = "All ${authRequests.size} authentication servers failed. " +
                    "Failures: ${failures.joinToString("; ") { "${it.statusCode ?: "N/A"}: ${it.reason}" }}"
        )
    }
}

/**
 * 构建器类，用于创建 ConcurrentAuthenticationManager
 */
class ConcurrentAuthenticationManagerBuilder {
    private val authRequests = mutableListOf<AuthenticationRequest>()
    private var globalTimeout: Duration = Duration.ofSeconds(30)

    fun addAuthRequest(request: AuthenticationRequest): ConcurrentAuthenticationManagerBuilder {
        authRequests.add(request)
        return this
    }

    fun addAuthRequests(requests: List<AuthenticationRequest>): ConcurrentAuthenticationManagerBuilder {
        authRequests.addAll(requests)
        return this
    }

    fun setGlobalTimeout(timeout: Duration): ConcurrentAuthenticationManagerBuilder {
        globalTimeout = timeout
        return this
    }

    fun build(): ConcurrentAuthenticationManager {
        return ConcurrentAuthenticationManager(authRequests, globalTimeout)
    }
}
