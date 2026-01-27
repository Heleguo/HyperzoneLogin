package icu.h2l.login.config.entry

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
class EntryConfig {

    @Comment("此验证服务的 ID，用于识别验证服务的唯一标识")
    var id: String = "Example"

    @Comment("验证服务的别称，用于一些指令结果或其他用途的内容显示")
    var name: String = "Unnamed"

    @Comment(
        """验证服务类型，支持以下值:
        Mojang - 官方 Yggdrasil Java 版账号验证服务
        Offline - 离线服务
        BLESSING_SKIN - Blessing Skin 的伪正版验证服务
        CUSTOM_YGGDRASIL - 自定义 Yggdrasil 伪正版验证服务
        FLOODGATE - Geyser 的 Floodgate（Xbox账号）验证服务"""
    )
    var serviceType: String = "Mojang"

    @Comment("Yggdrasil 类型账号验证服务配置")
    var yggdrasilAuth: YggdrasilAuthConfig = YggdrasilAuthConfig()
}
