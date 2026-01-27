package icu.h2l.login.config.entry

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
class CustomYggdrasilConfig {
    @Comment(
        """设置 Yggdrasil hasJoined 请求验证链接设置
        占位变量: {0}/{username}, {1}/{serverId}, {2}/{ip}"""
    )
    var url: String = "https://example.com/session/minecraft/hasJoined?username={0}&serverId={1}{2}"

    @Comment(
        """设置 Yggdrasil hasJoined 请求验证方式
        GET - 此方式被绝大多数验证服务器（包括官方）采用
        POST - 此方式被极少数验证服务器采用"""
    )
    var method: String = "GET"

    @Comment("设置 Yggdrasil hasJoined 的 url 和 postContent 节点 {ip} 变量内容")
    var trackIpContent: String = "&ip={0}"

    @Comment("设置 Yggdrasil hasJoined 发送 POST 验证请求的内容")
    var postContent: String = """{"username":"{0}", "serverId":"{1}"}"""
}
