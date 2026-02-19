package icu.h2l.login.listener

import com.velocitypowered.api.event.Subscribe
import `fun`.iiii.h2l.api.event.connection.OnlineAuthEvent
import `fun`.iiii.h2l.api.event.connection.OpenPreLoginEvent
import icu.h2l.login.HyperZoneLoginMain
import icu.h2l.login.type.OfflineUUIDType
import icu.h2l.login.util.ExtraUuidUtils
import icu.h2l.login.util.RemapUtils
import icu.h2l.login.util.info

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
    fun onPreLogin(event: OnlineAuthEvent) {
        val username = event.userName
        val serverId = event.serverId
        val playerIp = event.playerIp
        val uuid = event.userUUID

        // 先给临时 Profile，让玩家继续登录流程
        event.gameProfile = RemapUtils.genProfile(username, HyperZoneLoginMain.getRemapConfig().prefix)

        // 启动异步验证，AuthManager会自己处理并存储结果
        if (event.isOnline)//yggd验证才会触发
            HyperZoneLoginMain.getInstance().yggdrasilAuthModule.startYggdrasilAuth(username, uuid, serverId, playerIp)
//        我们需要 userName serverId playerIp来传递给下层用于验证

    }
} 