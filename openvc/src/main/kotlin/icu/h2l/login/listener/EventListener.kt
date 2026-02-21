package icu.h2l.login.listener

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.util.GameProfile
import icu.h2l.api.event.connection.HyperZoneGameProfileRequestEvent
import icu.h2l.api.event.connection.OpenPreLoginEvent
import icu.h2l.login.HyperZoneLoginMain
import icu.h2l.login.manager.HyperZonePlayerManager
import icu.h2l.login.type.OfflineUUIDType
import icu.h2l.login.util.ExtraUuidUtils
import icu.h2l.login.util.info
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class EventListener {
    @Subscribe
    fun onPreLogin(event: OpenPreLoginEvent) {
        val uuid = event.uuid
        val name = event.userName
        val host = event.host
        val offlineUUIDType = ExtraUuidUtils.matchType(uuid, name)

        val offlineHost = HyperZoneLoginMain.getInstance().loginServerManager.shouldOfflineHost(host)
        if (offlineHost) {
            info { "匹配到离线 host 玩家: $name" }
        }
        if (offlineUUIDType != OfflineUUIDType.UNKNOWN || offlineHost) {
            event.isOnline = false
        } else {
            event.isOnline = true
        }
        info { "传入 UUID 信息玩家: $name UUID:$uuid 类型: $offlineUUIDType 在线:${event.isOnline}" }
    }

    @Subscribe
    fun onPreLogin(event: HyperZoneGameProfileRequestEvent) {
        val hyperZonePlayer = HyperZonePlayerManager.getOrCreate(event.player)
        val originalProfile = event.originalProfile

        val resolvedProfile = hyperZonePlayer.getProfile()
        if (resolvedProfile == null) {
            HyperZoneLoginMain.getInstance().logger.error(
                "玩家 ${event.player.username} 在 GameProfileRequest 阶段未找到 Profile，已拒绝连接"
            )
            event.player.disconnect(
                Component.text("登录失败：未找到你的档案信息，请联系管理员。", NamedTextColor.RED)
            )
            return
        }

        event.gameProfile = GameProfile(
            resolvedProfile.uuid,
            resolvedProfile.name,
            originalProfile.properties,
        )
    }
}