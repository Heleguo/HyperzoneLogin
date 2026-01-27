package icu.h2l.login.config.entry

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
class ProxyConfig {
    @Comment(
        """设置代理类型
        DIRECT - 直接连接、或没有代理
        HTTP - 表示高级协议(如HTTP或FTP)的代理
        SOCKS - 表示一个SOCKS (V4或V5)代理"""
    )
    var type: String = "DIRECT"

    @Comment("代理服务器地址")
    var hostname: String = "127.0.0.1"

    @Comment("代理服务器端口")
    var port: Int = 1080

    @Comment("代理鉴权用户名，留空则不进行鉴权")
    var username: String = ""

    @Comment("代理鉴权密码")
    var password: String = ""
}
