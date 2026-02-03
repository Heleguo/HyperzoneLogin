# Limbo 命令系统 - 快速参�?

## 快速开�?

### 1️⃣ 创建命令（接口方式）

```kotlin
class MyCommand : LimboCommand {
    override val name = "mycommand"
    override val aliases = listOf("mc")
    override val description = "我的命令"
    override val usage = "mycommand [参数]"
    
    override fun execute(handler: LimboAuthSessionHandler, player: Player, args: Array<String>): Boolean {
        handler.sendMessage(Component.text("执行成功�?))
        return true
    }
}
```

### 2️⃣ 创建命令（DSL 方式�?

```kotlin
LimboCommandManager.command("test") {
    description("测试命令")
    aliases("t")
    execute { handler, player, args ->
        handler.sendMessage(Component.text("测试"))
        true
    }
}
```

### 3️⃣ 注册命令

```kotlin
// 单个注册
LimboCommandManager.registerCommand(MyCommand())

// 批量注册
LimboCommandManager.registerCommands(
    Command1(),
    Command2(),
    Command3()
)
```

### 4️⃣ Brigadier 集成

```kotlin
// 启用适配�?
BrigadierAdapter.enableBrigadier()

// 创建处理�?
val handler = object : SimpleBrigadierCommandHandler("cmd", "描述") {
    override fun execute(...) = true
}

// 注册
BrigadierAdapter.registerBrigadierCommands(mapOf("cmd" to handler))
```

## 常用 API

| 操作 | 代码 |
|------|------|
| 注册命令 | `LimboCommandManager.registerCommand(cmd)` |
| 取消注册 | `LimboCommandManager.unregisterCommand("name")` |
| 获取命令 | `LimboCommandManager.getCommand("name")` |
| 获取所有命�?| `LimboCommandManager.getAllCommands()` |
| 检查是否注�?| `LimboCommandManager.isCommandRegistered("name")` |
| 设置前缀 | `LimboCommandManager.setCommandPrefix("/")` |
| 严格模式 | `LimboCommandManager.setStrictMode(true)` |
| 添加监听�?| `LimboCommandManager.addRegistrationListener(...)` |

## 内置命令

| 命令 | 别名 | 说明 |
|------|------|------|
| help | ?, h | 显示帮助 |
| login | l, 登录 | 开始登�?|
| exit | quit, logout, 退�?| 退出游�?|
| info | information, me, 信息 | 显示信息 |

## 命令接口方法

```kotlin
interface LimboCommand {
    val name: String                        // 必须：命令名�?
    val aliases: List<String>               // 可选：别名
    val description: String                 // 可选：描述
    val usage: String                       // 可选：用法
    
    fun execute(...): Boolean               // 必须：执行逻辑
    fun hasPermission(...): Boolean         // 可选：权限检�?
    fun onTabComplete(...): List<String>    // 可选：Tab补全
}
```

## DSL 构建器方�?

```kotlin
LimboCommandManager.command("name") {
    description("...")              // 设置描述
    usage("...")                    // 设置用法
    aliases("a", "b")              // 设置别名
    permission("node")             // 权限节点
    permission { player -> ... }   // 自定义权�?
    execute { h, p, args -> ... }  // 执行逻辑
    tabComplete { p, args -> ... } // Tab补全
}
```

## 发送消�?

```kotlin
// 发送普通消�?
handler.sendMessage(Component.text("消息"))

// 发送带颜色的消�?
handler.sendMessage(Component.text("错误", NamedTextColor.RED))
handler.sendMessage(Component.text("成功", NamedTextColor.GREEN))
handler.sendMessage(Component.text("警告", NamedTextColor.YELLOW))

// 组合消息
handler.sendMessage(
    Component.text("前缀: ", NamedTextColor.GOLD)
        .append(Component.text("内容", NamedTextColor.WHITE))
)
```

## 参数处理

```kotlin
override fun execute(handler: LimboAuthSessionHandler, player: Player, args: Array<String>): Boolean {
    // 检查参数数�?
    if (args.isEmpty()) {
        handler.sendMessage(Component.text("缺少参数"))
        return false  // 返回 false 会显�?usage
    }
    
    // 获取参数
    val arg1 = args[0]
    val arg2 = args.getOrNull(1) ?: "默认�?
    
    // 合并所有参�?
    val allArgs = args.joinToString(" ")
    
    return true
}
```

## 权限检�?

```kotlin
// 方式1：重写方�?
override fun hasPermission(player: Player): Boolean {
    return player.hasPermission("hyperzonelogin.mycommand")
}

// 方式2：DSL
permission("hyperzonelogin.mycommand")

// 方式3：自定义逻辑
permission { player ->
    player.hasPermission("admin") || player.username == "ksqeib"
}
```

## 自定义命令处理器

```kotlin
// 为特定会话创建自定义处理�?
val customHandler = LimboCommandHandler()
customHandler.registerCommand(MyCommand())
customHandler.commandPrefix = "/"
customHandler.strictMode = true

// 应用到会�?
limboAuthSessionHandler.setCommandHandler(customHandler)
```

## 命令监听�?

```kotlin
LimboCommandManager.addRegistrationListener(object : CommandRegistrationListener {
    override fun onCommandRegistered(command: LimboCommand) {
        println("已注�? ${command.name}")
    }
    
    override fun onCommandUnregistered(commandName: String) {
        println("已取�? $commandName")
    }
})
```

## 未知命令处理

```kotlin
LimboCommandManager.setUnknownCommandHandler { handler, player, message ->
    handler.sendMessage(Component.text("未知命令: $message", NamedTextColor.RED))
    handler.sendMessage(Component.text("输入 'help' 查看帮助", NamedTextColor.GRAY))
}
```

## 示例：完整命�?

```kotlin
class RegisterCommand : LimboCommand {
    override val name = "register"
    override val aliases = listOf("reg", "注册")
    override val description = "注册账户"
    override val usage = "register <密码> <确认密码>"
    
    override fun execute(handler: LimboAuthSessionHandler, player: Player, args: Array<String>): Boolean {
        if (args.size < 2) {
            handler.sendMessage(Component.text("用法: $usage", NamedTextColor.YELLOW))
            return false
        }
        
        val password = args[0]
        val confirm = args[1]
        
        if (password != confirm) {
            handler.sendMessage(Component.text("密码不匹�?, NamedTextColor.RED))
            return true
        }
        
        if (password.length < 6) {
            handler.sendMessage(Component.text("密码太短", NamedTextColor.RED))
            return true
        }
        
        // 执行注册逻辑
        handler.sendMessage(Component.text("注册成功�?, NamedTextColor.GREEN))
        return true
    }
}
```

## 调试技�?

```kotlin
// 打印所有已注册的命�?
LimboCommandManager.getAllCommands().forEach { cmd ->
    println("命令: ${cmd.name}, 别名: ${cmd.aliases}")
}

// 检查命令是否注�?
if (LimboCommandManager.isCommandRegistered("mycommand")) {
    println("命令已注�?)
}

// 获取命令详情
val cmd = LimboCommandManager.getCommand("mycommand")
println("描述: ${cmd?.description}")
```

## 最佳实�?

�?**推荐做法**
- 使用简短、直观的命令�?
- 为常用命令提供别�?
- �?execute 中验证参�?
- 返回 false 时让系统显示 usage
- 使用适当的颜色区分消息类�?

�?**不推荐做�?*
- 命令名过长或难记
- 在命令中执行耗时操作（应异步�?
- 忽略异常处理
- 硬编码消息（应支持配�?国际化）
- �?execute 中直接操作数据库（应通过管理器）

## 常见问题

**Q: 命令无响应？**
A: 检查命令是否已注册，是否通过权限检�?

**Q: 如何支持子命令？**
A: �?execute 中检�?args[0] 并分�?

**Q: 如何异步执行�?*
A: 使用协程或线程池

```kotlin
execute { handler, player, args ->
    CoroutineScope(Dispatchers.IO).launch {
        // 异步操作
        withContext(Dispatchers.Default) {
            handler.sendMessage(Component.text("完成"))
        }
    }
    true
}
```

---

📚 更多详情请查看：
- [完整使用文档](LIMBO_COMMAND_USAGE.md)
- [架构说明](LIMBO_COMMAND_ARCHITECTURE.md)

