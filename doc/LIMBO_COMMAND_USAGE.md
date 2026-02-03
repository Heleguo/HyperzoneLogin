# Limbo 命令系统使用文档

## 概述

Limbo 命令系统是一个强大且灵活的命令框架，用于�?Limbo 状态下处理玩家输入。该系统支持�?

- 无需前缀的命令解�?
- 命令注册和管�?
- 命令别名
- 权限检�?
- Tab 补全（预留接口）
- �?Brigadier 等外部系统集�?

## 核心组件

### 1. LimboCommand 接口

所有命令都需要实现这个接口：

```kotlin
interface LimboCommand {
    val name: String                    // 命令名称
    val aliases: List<String>           // 命令别名
    val description: String             // 命令描述
    val usage: String                   // 使用方法
    
    fun execute(handler: LimboAuthSessionHandler, player: Player, args: Array<String>): Boolean
    fun hasPermission(player: Player): Boolean
    fun onTabComplete(player: Player, args: Array<String>): List<String>
}
```

### 2. LimboCommandHandler �?

负责解析和执行命令：

- 支持命令前缀（可选）
- 支持严格模式（必须有前缀�?
- 支持自定义未知命令处理器
- 智能参数解析（支持引号包裹的参数�?

### 3. LimboCommandManager 对象

全局命令管理器：

- 命令注册和取消注�?
- 命令查询
- 命令监听器（用于外部系统集成�?
- DSL 风格的命令构�?

## 使用方式

### 方式一：实�?LimboCommand 接口

```kotlin
class MyCommand : LimboCommand {
    override val name: String = "mycommand"
    override val aliases: List<String> = listOf("mc", "mycmd")
    override val description: String = "这是我的命令"
    override val usage: String = "mycommand <参数>"
    
    override fun execute(
        handler: LimboAuthSessionHandler,
        player: Player,
        args: Array<String>
    ): Boolean {
        handler.sendMessage(Component.text("命令执行成功�?))
        return true
    }
}

// 注册命令
LimboCommandManager.registerCommand(MyCommand())
```

### 方式二：使用 DSL 构建�?

```kotlin
LimboCommandManager.command("test") {
    description("测试命令")
    usage("test <消息>")
    aliases("t")
    permission("hyperzonelogin.test")
    
    execute { handler, player, args ->
        if (args.isEmpty()) {
            handler.sendMessage(Component.text("请提供参�?))
            return@execute false
        }
        
        handler.sendMessage(Component.text("收到: ${args.joinToString(" ")}"))
        true
    }
}
```

### 方式三：通过 Brigadier 集成

```kotlin
// 启用 Brigadier 适配�?
BrigadierAdapter.enableBrigadier()

// 创建 Brigadier 命令处理�?
val myHandler = object : SimpleBrigadierCommandHandler("mycommand", "我的命令") {
    override fun execute(
        handler: LimboAuthSessionHandler,
        player: Player,
        args: Array<String>
    ): Boolean {
        handler.sendMessage(Component.text("Brigadier 命令执行"))
        return true
    }
}

// 注册
val command = BrigadierAdapter.createBrigadierCommand("mycommand", myHandler)
LimboCommandManager.registerCommand(command)

// 或批量注�?
BrigadierAdapter.registerBrigadierCommands(mapOf(
    "cmd1" to handler1,
    "cmd2" to handler2
))
```

## 内置命令

系统默认提供以下命令�?

1. **help** (别名: ?, h)
   - 功能：显示所有可用命令或特定命令的详细信�?
   - 用法：`help [命令名称]`

2. **login** (别名: l, 登录)
   - 功能：开�?Yggdrasil 验证流程
   - 用法：`login`

3. **exit** (别名: quit, logout, 退�?
   - 功能：退出游�?
   - 用法：`exit`

4. **info** (别名: information, me, 信息)
   - 功能：显示玩家信�?
   - 用法：`info`

## 配置选项

```kotlin
// 设置命令前缀（例�?"/"�?
LimboCommandManager.setCommandPrefix("/")

// 设置严格模式（必须有前缀才能执行命令�?
LimboCommandManager.setStrictMode(true)

// 设置未知命令处理�?
LimboCommandManager.setUnknownCommandHandler { handler, player, message ->
    handler.sendMessage(Component.text("未知命令: $message"))
}
```

## 命令监听�?

监听命令注册事件，用于与外部系统集成�?

```kotlin
LimboCommandManager.addRegistrationListener(object : LimboCommandManager.CommandRegistrationListener {
    override fun onCommandRegistered(command: LimboCommand) {
        println("命令已注�? ${command.name}")
    }
    
    override fun onCommandUnregistered(commandName: String) {
        println("命令已取消注�? $commandName")
    }
})
```

## 自定义命令处理器

每个 `LimboAuthSessionHandler` 可以有自己的命令处理器：

```kotlin
val handler = LimboAuthSessionHandler(player)

// 创建自定义命令处理器
val customHandler = LimboCommandHandler()
customHandler.registerCommand(MyCustomCommand())

// 设置自定义处理器
handler.setCommandHandler(customHandler)
```

## 高级特�?

### 参数解析

命令系统支持智能参数解析�?

```
命令: echo "hello world" test
参数: ["hello world", "test"]

命令: echo hello\ world test
参数: ["hello world", "test"]
```

### 权限检�?

```kotlin
override fun hasPermission(player: Player): Boolean {
    return player.hasPermission("hyperzonelogin.mycommand")
}
```

### Tab 补全（预留）

```kotlin
override fun onTabComplete(player: Player, args: Array<String>): List<String> {
    return when (args.size) {
        1 -> listOf("option1", "option2", "option3")
        2 -> listOf("value1", "value2")
        else -> emptyList()
    }
}
```

## 集成到主程序

�?`HyperZoneLoginMain.kt` 中已经自动初始化�?

```kotlin
private fun initializeLimboCommands() {
    logger.info("正在初始�?Limbo 命令系统...")
    
    // 注册所有内置命�?
    LimboCommandInitializer.registerDefaultCommands()
    
    // 可选：注册 DSL 示例命令
    // LimboCommandInitializer.registerExampleDSLCommands()
    
    // 可选：启用 Brigadier 集成
    // BrigadierAdapter.enableBrigadier()
    
    logger.info("Limbo 命令系统初始化完�?)
}
```

## 最佳实�?

1. **命令名称**: 使用简短、直观的命令名称
2. **别名**: 为常用命令提供简短别�?
3. **权限**: 敏感操作应该检查权�?
4. **错误处理**: �?execute 方法中妥善处理异�?
5. **返回�?*: execute 返回 false 时会显示 usage 信息
6. **国际�?*: 考虑支持多语言命令和消�?

## 示例：创建一个完整的命令

```kotlin
package icu.h2l.login.limbo.command.commands

import com.velocitypowered.api.proxy.Player
import icu.h2l.login.limbo.command.LimboCommand
import icu.h2l.login.limbo.handler.LimboAuthSessionHandler
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class RegisterCommand : LimboCommand {
    override val name: String = "register"
    override val aliases: List<String> = listOf("reg", "注册")
    override val description: String = "注册新账�?
    override val usage: String = "register <密码> <确认密码>"
    
    override fun execute(
        handler: LimboAuthSessionHandler,
        player: Player,
        args: Array<String>
    ): Boolean {
        // 检查参数数�?
        if (args.size < 2) {
            handler.sendMessage(
                Component.text("请提供密码和确认密码", NamedTextColor.RED)
            )
            return false
        }
        
        val password = args[0]
        val confirmPassword = args[1]
        
        // 验证密码
        if (password != confirmPassword) {
            handler.sendMessage(
                Component.text("两次密码不一致！", NamedTextColor.RED)
            )
            return true // 返回 true 表示命令已处理，不显�?usage
        }
        
        if (password.length < 6) {
            handler.sendMessage(
                Component.text("密码长度至少�?6 位！", NamedTextColor.RED)
            )
            return true
        }
        
        // 执行注册逻辑
        try {
            // TODO: 实际的注册逻辑
            handler.sendMessage(
                Component.text("注册成功�?, NamedTextColor.GREEN)
            )
        } catch (e: Exception) {
            handler.sendMessage(
                Component.text("注册失败: ${e.message}", NamedTextColor.RED)
            )
        }
        
        return true
    }
    
    override fun hasPermission(player: Player): Boolean {
        // 注册命令所有人都可以使�?
        return true
    }
}
```

注册该命令：

```kotlin
LimboCommandManager.registerCommand(RegisterCommand())
```

