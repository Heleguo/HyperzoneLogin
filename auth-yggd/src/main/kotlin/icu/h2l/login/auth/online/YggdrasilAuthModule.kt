package icu.h2l.login.auth.online

import com.velocitypowered.api.util.GameProfile
import com.velocitypowered.proxy.VelocityServer
import `fun`.iiii.h2l.api.log.debug
import `fun`.iiii.h2l.api.log.info
import icu.h2l.login.auth.online.manager.EntryConfigManager
import icu.h2l.login.auth.online.req.AuthServerConfig
import icu.h2l.login.auth.online.req.AuthenticationRequest
import icu.h2l.login.auth.online.req.AuthenticationResult
import icu.h2l.login.auth.online.req.ConcurrentAuthenticationManager
import icu.h2l.login.auth.online.req.MojangStyleAuthRequest
import icu.h2l.login.config.entry.EntryConfig
import icu.h2l.login.limbo.handler.LimboAuthSessionHandler
import icu.h2l.login.manager.DatabaseManager
import icu.h2l.login.manager.EntryConfigManager
import kotlinx.coroutines.*
import net.kyori.adventure.text.Component
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import java.net.http.HttpClient
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.iterator
import kotlin.compareTo
import kotlin.or

/**
 * 验证管理器
 * 负责管理玩家的一层登入状态和Yggdrasil验证逻辑
 */
class YggdrasilAuthModule(
    private val entryConfigManager: EntryConfigManager,
    private val databaseManager: DatabaseManager
) {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    private val gson = VelocityServer.GENERAL_GSON

    /**
     * 玩家一层登入状态
     * Key: 玩家用户名
     * Value: 是否已通过一层验证
     */
    private val firstLevelAuthStatus = ConcurrentHashMap<String, Boolean>()

    /**
     * 玩家验证成功的Entry ID
     * Key: 玩家用户名
     * Value: Entry ID
     */
    private val playerEntryMapping = ConcurrentHashMap<String, String>()

    /**
     * 存储验证结果
     * Key: 玩家用户名
     * Value: 验证结果
     */
    private val authResults = ConcurrentHashMap<String, YggdrasilAuthResult>()

    /**
     * 存储LimboAuthSessionHandler实例
     * Key: 玩家用户名
     * Value: LimboAuthSessionHandler实例
     */
    private val limboHandlers = ConcurrentHashMap<String, LimboAuthSessionHandler>()

    /**
     * 协程作用域
     */
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * 启动异步Yggdrasil验证（不阻塞）
     * 
     * @param username 玩家用户名
     * @param uuid 玩家UUID
     * @param serverId 服务器ID
     * @param playerIp 玩家IP
     */
    fun startYggdrasilAuth(
        username: String,
        uuid: UUID,
        serverId: String,
        playerIp: String? = null
    ) {
        coroutineScope.launch {
            val result = performYggdrasilAuth(username, uuid, serverId, playerIp)
            authResults[username] = result

            if (result is YggdrasilAuthResult.Success) {
                info { "玩家 $username 通过 Yggdrasil 验证，Entry: ${result.entryId}" }

                // 验证成功，调用LimboHandler的overVerify方法
                limboHandlers[username]?.overVerify()
            } else {
                limboHandlers[username]?.sendMessage(Component.text("玩家 $username Yggdrasil 验证失败"))
                info { "玩家 $username Yggdrasil 验证失败" }
            }
        }
    }

    /**
     * 获取玩家的验证结果
     * 
     * @param username 玩家用户名
     * @return 验证结果，如果还未验证完成则返回null
     */
    fun getAuthResult(username: String): YggdrasilAuthResult? {
        return authResults[username]
    }

    /**
     * 注册玩家的LimboAuthSessionHandler实例
     * 应该在玩家开始验证时就调用此方法
     * 
     * @param username 玩家用户名
     * @param handler LimboAuthSessionHandler实例
     */
    fun registerLimboHandler(username: String, handler: LimboAuthSessionHandler) {
        limboHandlers[username] = handler
        debug { "为玩家 $username 注册 LimboAuthSessionHandler" }
    }

    /**
     * 获取玩家的LimboAuthSessionHandler实例
     * 
     * @param username 玩家用户名
     * @return LimboAuthSessionHandler实例，如果未注册则返回null
     */
    fun getLimboHandler(username: String): LimboAuthSessionHandler? {
        return limboHandlers[username]
    }

    /**
     * 设置玩家的一层登入状态
     * 
     * @param username 玩家用户名
     * @param status 验证状态
     * @param entryId 验证成功的Entry ID（可选）
     */
    fun setFirstLevelAuthStatus(username: String, status: Boolean, entryId: String? = null) {
        firstLevelAuthStatus[username] = status
        if (status && entryId != null) {
            playerEntryMapping[username] = entryId
            info { "玩家 $username 通过一层验证，Entry: $entryId" }
        }
    }

    /**
     * 获取玩家的一层登入状态
     * 
     * @param username 玩家用户名
     * @return 是否已通过一层验证
     */
    fun getFirstLevelAuthStatus(username: String): Boolean {
        return firstLevelAuthStatus.getOrDefault(username, false)
    }

    /**
     * 清除玩家的验证状态
     * 
     * @param username 玩家用户名
     */
    fun clearAuthStatus(username: String) {
        firstLevelAuthStatus.remove(username)
        playerEntryMapping.remove(username)
        authResults.remove(username)
        limboHandlers.remove(username)
    }

    /**
     * 获取玩家验证成功的Entry ID
     * 
     * @param username 玩家用户名
     * @return Entry ID，如果未验证则返回null
     */
    fun getPlayerEntry(username: String): String? {
        return playerEntryMapping[username]
    }

    /**（内部方法，由startYggdrasilAuth调用）
     * 
     * 验证逻辑分为两个批次：
     * 1. 第一批次：查询数据库中是否有该玩家的记录（通过UUID或用户名），
     *    如果有，则向对应的Entry服务器发起验证请求
     * 2. 第二批次：如果第一批次没有找到或验证失败，
     *    则向所有配置的Yggdrasil服务器发起验证请求
     * 
     * @param username 玩家用户名
     * @param uuid 玩家UUID
     * @param serverId 服务器ID
     * @param playerIp 玩家IP
     * @return 验证结果
     */
    private fun performYggdrasilAuth(
        username: String,
        uuid: UUID,
        serverId: String,
        playerIp: String? = null
    ): YggdrasilAuthResult = runBlocking {
        debug { "开始对玩家 $username (UUID: $uuid) 进行Yggdrasil验证" }

        // 第一批次：从数据库查找已有记录
        val knownEntries = findEntriesInDatabase(username, uuid)

        if (knownEntries.isNotEmpty()) {
            debug { "玩家 $username 在数据库中找到 ${knownEntries.size} 个Entry记录" }

            // 构建第一批次的验证请求
            val firstBatchRequests = buildAuthRequests(knownEntries)

            if (firstBatchRequests.isNotEmpty()) {
                val firstBatchResult = executeAuthRequests(
                    username, serverId, playerIp, firstBatchRequests, "第一批次"
                )

                if (firstBatchResult.isSuccess) {
                    return@runBlocking firstBatchResult
                }
            }
        }

        debug { "第一批次验证未通过，开始第二批次（所有Yggdrasil服务器）" }

        // 第二批次：向所有Yggdrasil服务器发起请求
        val allYggdrasilEntries = getAllYggdrasilEntries()
        val secondBatchRequests = buildAuthRequests(allYggdrasilEntries)

        if (secondBatchRequests.isEmpty()) {
            return@runBlocking YggdrasilAuthResult.NoServersConfigured
        }

        executeAuthRequests(username, serverId, playerIp, secondBatchRequests, "第二批次")
    }

    /**
     * 从数据库中查找玩家相关的Entry记录
     * 
     * @param username 玩家用户名
     * @param uuid 玩家UUID
     * @return Entry ID列表
     */
    private fun findEntriesInDatabase(username: String, uuid: UUID): List<String> {
        val foundEntries = mutableSetOf<String>()

        // 获取所有已注册的Entry表
        val allEntries = entryConfigManager.getAllConfigs()

        databaseManager.executeTransaction {
            for ((configName, entryConfig) in allEntries) {
                val entryTable = databaseManager.getEntryTable(entryConfig.id.lowercase())

                if (entryTable != null) {
                    // 查询是否有匹配的记录（通过用户名或UUID）
                    val hasRecord =
                        entryTable.selectAll().where { (entryTable.name eq username) or (entryTable.uuid eq uuid) }
                            .count() compareTo 0

                    if (hasRecord) {
                        foundEntries.add(entryConfig.id)
                        debug { "在Entry表 ${entryConfig.id} 中找到玩家 $username 的记录" }
                    }
                }
            }
        }

        return foundEntries.toList()
    }

    /**
     * 获取所有配置的Yggdrasil Entry
     * 
     * @return Entry配置列表
     */
    private fun getAllYggdrasilEntries(): List<EntryConfig> {
        return entryConfigManager.getAllConfigs().values.toList()
    }

    /**
     * 构建验证请求列表
     * 
     * @param entries Entry ID列表或Entry配置列表
     * @return AuthenticationRequest列表
     */
    private fun buildAuthRequests(entries: List<Any>): List<Pair<String, AuthenticationRequest>> {
        val requests = mutableListOf<Pair<String, AuthenticationRequest>>()

        for (entry in entries) {
            val entryConfig = when (entry) {
                is String -> entryConfigManager.getConfigById(entry)
                is EntryConfig -> entry
                else -> null
            } ?: continue

            // 构建AuthServerConfig
            val authServerConfig = AuthServerConfig(
                url = entryConfig.yggdrasilAuth.url,
                name = entryConfig.name,
                connectTimeout = Duration.ofSeconds(5),
                readTimeout = Duration.ofSeconds(10)
            )

            // 创建MojangStyleAuthRequest
            val authRequest = MojangStyleAuthRequest(
                config = authServerConfig,
                httpClient = httpClient,
                gson = gson
            )

            requests.add(Pair(entryConfig.id, authRequest))
        }

        return requests
    }

    /**
     * 执行验证请求（并发）
     * 
     * @param username 玩家用户名
     * @param serverId 服务器ID
     * @param playerIp 玩家IP
     * @param requests 验证请求列表
     * @param batchName 批次名称（用于日志）
     * @return 验证结果
     */
    private suspend fun executeAuthRequests(
        username: String,
        serverId: String,
        playerIp: String?,
        requests: List<Pair<String, AuthenticationRequest>>,
        batchName: String
    ): YggdrasilAuthResult {
        debug { "$batchName: 开始并发验证，共 ${requests.size} 个服务器" }

        // 创建并发验证管理器
        val authManager = ConcurrentAuthenticationManager(
            authRequests = requests.map { it.second },
            globalTimeout = Duration.ofSeconds(30)
        )

        // 执行并发验证
        val result = authManager.authenticate(username, serverId, playerIp)

        return when (result) {
            is AuthenticationResult.Success -> {
                // 找到成功验证的Entry ID
                val successIndex = requests.indexOfFirst {
                    it.second.toString() == result.serverUrl ||
                            result.serverUrl.contains(it.first)
                }

                val entryId = if (successIndex >= 0) {
                    requests[successIndex].first
                } else {
                    // 尝试从URL中提取
                    requests.firstOrNull()?.first ?: "unknown"
                }

                setFirstLevelAuthStatus(username, true, entryId)

                YggdrasilAuthResult.Success(
                    profile = result.profile,
                    entryId = entryId,
                    serverUrl = result.serverUrl
                )
            }

            is AuthenticationResult.Failure -> {
                YggdrasilAuthResult.Failed(
                    reason = result.reason,
                    statusCode = result.statusCode
                )
            }

            is AuthenticationResult.Timeout -> {
                YggdrasilAuthResult.Timeout
            }
        }
    }
}

/**
 * Yggdrasil验证结果
 */
sealed class YggdrasilAuthResult {
    /**
     * 验证成功
     */
    data class Success(
        val profile: GameProfile,
        val entryId: String,
        val serverUrl: String
    ) : YggdrasilAuthResult() {
    }

    /**
     * 验证失败
     */
    data class Failed(
        val reason: String,
        val statusCode: Int? = null
    ) : YggdrasilAuthResult() {
    }

    /**
     * 验证超时
     */
    object Timeout : YggdrasilAuthResult() {
    }

    /**
     * 没有配置的服务器
     */
    object NoServersConfigured : YggdrasilAuthResult() {
    }

    val isSuccess: Boolean
        get() = this is Success
}
