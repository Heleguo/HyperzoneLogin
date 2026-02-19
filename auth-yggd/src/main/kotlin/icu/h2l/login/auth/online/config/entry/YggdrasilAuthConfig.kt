package icu.h2l.login.auth.online.config.entry

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
class YggdrasilAuthConfig {
    @Comment(
        """Yggdrasil hasJoined 验证 URL
        占位变量: {username}, {serverId}, {ip}
        
        常见模板：
        Mojang: https://sessionserver.mojang.com/session/minecraft/hasJoined?username={username}&serverId={serverId}{ip}
        BlessingSkin: https://example.com/api/yggdrasil/sessionserver/session/minecraft/hasJoined?username={username}&serverId={serverId}{ip}
        其他: https://example.com/session/minecraft/hasJoined?username={username}&serverId={serverId}{ip}"""
    )
    var url: String = "https://sessionserver.mojang.com/session/minecraft/hasJoined?username={username}&serverId={serverId}"

    @Comment("验证请求超时时间（毫秒）")
    var timeout: Int = 10000

    @Comment("网络错误时的重试次数")
    var retry: Int = 0

    @Comment("重试请求延迟（毫秒）")
    var retryDelay: Int = 0

    @Comment("代理设置")
    var authProxy: ProxyConfig = ProxyConfig()
}
