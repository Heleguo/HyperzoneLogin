package icu.h2l.login.auth.online.config.entry

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
class EntryConfig {

    @Comment("此验证服务的 ID(无论大小写，到数据库会变成小写)，用于识别验证服务的唯一标识")
    var id: String = "Example"

    @Comment("验证服务的别称，用于一些指令结果或其他用途的内容显示")
    var name: String = "Unnamed"

    @Comment("Yggdrasil 类型账号验证服务配置（当 serviceType 为 Yggdrasil 时有效）")
    var yggdrasilAuth: YggdrasilAuthConfig = YggdrasilAuthConfig()
}