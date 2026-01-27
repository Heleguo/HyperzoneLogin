package icu.h2l.login.config.entry

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
class YggdrasilAuthConfig {
    @Comment("Yggdrasil 在 hasJoined 阶段时请求是否追加用户 IP 信息")
    var trackIp: Boolean = false

    @Comment("设置 Yggdrasil hasJoined 验证超时时间")
    var timeout: Int = 10000

    @Comment("设置 Yggdrasil hasJoined 网络错误时的重试次数")
    var retry: Int = 0

    @Comment("设置 Yggdrasil hasJoined 重试请求延迟")
    var retryDelay: Int = 0

    @Comment("OFFICIAL 专用设置")
    var official: OfficialConfig = OfficialConfig()

    @Comment("BLESSING_SKIN 专用设置")
    var blessingSkin: BlessingSkinConfig = BlessingSkinConfig()

    @Comment("CUSTOM_YGGDRASIL 专用设置")
    var custom: CustomYggdrasilConfig = CustomYggdrasilConfig()

    @Comment("设置 Yggdrasil hasJoined 代理")
    var authProxy: ProxyConfig = ProxyConfig()
}
