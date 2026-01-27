package icu.h2l.login.config.entry

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
class BlessingSkinConfig {
    @Comment("指定 Blessing Skin Yggdrasil API 地址")
    var apiRoot: String = "https://example.com/api/yggdrasil"
}
