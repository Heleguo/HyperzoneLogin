package icu.h2l.login.config.entry

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
class OfficialConfig {
    @Comment("可选，指定自定义 Session 验证服务器地址")
    var sessionServer: String = "https://sessionserver.mojang.com"
}
