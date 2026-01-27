# 多服务器并发验证系统

## 概述

这是一个使用 Kotlin 编写的多服务器并发验证系统，能够同时向多个 Minecraft 验证服务器发送请求，当任意服务器返回成功时立即终止其他请求并返回结果。

## 架构设计

系统采用模块化设计，分为以下几个核心组件：

### 1. 数据类和结果封装

- **AuthenticationResult.kt**: 验证结果的密封类
  - `Success`: 验证成功，包含玩家档案和成功的服务器URL
  - `Failure`: 验证失败，包含失败原因和HTTP状态码
  - `Timeout`: 验证超时，包含尝试的服务器列表

- **AuthServerConfig**: 验证服务器配置
  - URL、名称、连接超时、读取超时等配置

### 2. 请求接口

- **AuthenticationRequest.kt**: 验证请求接口
  - 定义了 `authenticate()` 方法，用于执行验证

### 3. 请求实现

- **MojangStyleAuthRequest.kt**: Mojang 风格验证实现
  - 实现了标准的 Mojang 验证流程
  - 使用 Java HttpClient 进行网络请求
  - 支持 URL 参数转义和自定义 User-Agent

### 4. 并发管理器

- **ConcurrentAuthenticationManager.kt**: 核心并发管理器
  - 使用 Kotlin 协程实现并发请求
  - 支持"赢者通吃"策略：第一个成功的结果返回，其他请求立即取消
  - 全局超时控制
  - 详细的日志记录

### 5. 使用示例

- **MultiServerAuthExample.kt**: 使用示例和工具方法

## 特性

✅ **并发请求**: 同时向多个服务器发送请求，提高成功率和响应速度  
✅ **快速响应**: 任意服务器成功立即返回，无需等待其他服务器  
✅ **自动取消**: 成功后自动取消其他未完成的请求，节省资源  
✅ **超时控制**: 全局超时设置，防止无限等待  
✅ **错误处理**: 完善的异常处理和错误信息收集  
✅ **类型安全**: 使用 Kotlin 密封类确保类型安全  
✅ **协程支持**: 基于 Kotlin 协程，高效且易于使用  

## 使用方法

### 基本使用

```kotlin
import icu.h2l.login.auth.*
import kotlinx.coroutines.runBlocking

// 1. 创建验证管理器
val manager = MultiServerAuthExample.createDefaultManager()

// 2. 执行验证
runBlocking {
    val result = manager.authenticate(
        username = "PlayerName",
        serverId = "generated-server-id",
        playerIp = "127.0.0.1" // 可选
    )
    
    when (result) {
        is AuthenticationResult.Success -> {
            println("验证成功: ${result.profile.name}")
            // 继续登录流程...
        }
        is AuthenticationResult.Failure -> {
            println("验证失败: ${result.reason}")
            // 断开连接...
        }
        is AuthenticationResult.Timeout -> {
            println("验证超时")
            // 断开连接...
        }
    }
}
```

### 自定义服务器配置

```kotlin
import java.net.http.HttpClient
import java.time.Duration

val httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(5))
    .build()

val serverConfigs = listOf(
    AuthServerConfig(
        url = "https://sessionserver.mojang.com/session/minecraft/hasJoined",
        name = "Mojang Official",
        connectTimeout = Duration.ofSeconds(5),
        readTimeout = Duration.ofSeconds(10)
    ),
    AuthServerConfig(
        url = "https://your-custom-server.com/hasJoined",
        name = "Custom Server",
        connectTimeout = Duration.ofSeconds(3),
        readTimeout = Duration.ofSeconds(8)
    )
)

val authRequests = serverConfigs.map { config ->
    MojangStyleAuthRequest(config, httpClient, gson, "MyProxy/1.0")
}

val manager = ConcurrentAuthenticationManagerBuilder()
    .addAuthRequests(authRequests)
    .setGlobalTimeout(Duration.ofSeconds(30))
    .build()
```

### 在 Velocity 中集成

```kotlin
// 在处理 EncryptionResponse 时
val authManager = MultiServerAuthExample.createDefaultManager(
    userAgent = "${server.version.name}/${server.version.version}",
    gson = GENERAL_GSON
)

// 执行验证
val result = withContext(mcConnection.eventLoop().asCoroutineDispatcher()) {
    authManager.authenticate(
        username = login.username,
        serverId = serverId,
        playerIp = if (server.configuration.shouldPreventClientProxyConnections()) {
            (mcConnection.remoteAddress as InetSocketAddress).hostString
        } else null
    )
}

when (result) {
    is AuthenticationResult.Success -> {
        // 验证成功，继续登录流程
        mcConnection.setActiveSessionHandler(
            StateRegistry.LOGIN,
            AuthSessionHandler(server, inbound, result.profile, true)
        )
    }
    is AuthenticationResult.Failure -> {
        // 验证失败
        if (result.statusCode == 204) {
            inbound.disconnect(
                Component.translatable("velocity.error.online-mode-only")
            )
        } else {
            inbound.disconnect(
                Component.translatable("multiplayer.disconnect.authservers_down")
            )
        }
    }
    is AuthenticationResult.Timeout -> {
        // 超时
        inbound.disconnect(
            Component.translatable("multiplayer.disconnect.authservers_down")
        )
    }
}
```

## 工作流程

1. **初始化**: 创建多个 `AuthenticationRequest` 实例，每个对应一个验证服务器
2. **并发请求**: `ConcurrentAuthenticationManager` 为每个请求创建一个协程
3. **竞争机制**: 所有协程并发执行，使用 `AtomicBoolean` 确保只有第一个成功的结果被采用
4. **快速终止**: 一旦有成功结果，立即取消其他所有未完成的协程
5. **超时控制**: 如果在全局超时时间内没有成功，返回 `Timeout` 结果
6. **结果返回**: 返回成功结果或综合的失败信息

## 线程安全

- 使用 `AtomicBoolean` 进行原子操作
- 使用 `Mutex` 保护共享状态
- 协程安全的取消机制

## 性能优化

- 使用协程而非线程，减少资源消耗
- 成功后立即取消其他请求，避免浪费
- 可复用 `HttpClient` 实例
- 支持连接池和 HTTP/2

## 扩展性

可以轻松添加新的验证服务器实现：

```kotlin
class CustomAuthRequest(
    private val config: AuthServerConfig
) : AuthenticationRequest {
    override suspend fun authenticate(
        username: String,
        serverId: String,
        playerIp: String?
    ): AuthenticationResult {
        // 自定义验证逻辑
        return AuthenticationResult.Success(...)
    }
}
```

## 依赖

- Kotlin 标准库
- Kotlin 协程 (kotlinx-coroutines-core)
- Velocity API
- Gson
- Java 11+ HttpClient

## 许可证

与原始 Velocity 项目相同，使用 GPLv3 许可证。
