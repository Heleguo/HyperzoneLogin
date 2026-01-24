package `fun`.iiii.mixedlogin.listener

import com.velocitypowered.api.event.Subscribe
import `fun`.iiii.mixedlogin.MixedLoginMain
import `fun`.iiii.mixedlogin.type.OfflineUUIDType
import `fun`.iiii.mixedlogin.util.ExtraUuidUtils
import `fun`.iiii.openvelocity.api.event.connection.OpenPreLoginEvent

class EventListener {
    @Subscribe
    fun onPreLogin(event: OpenPreLoginEvent) {
        val uuid = event.uuid
        val name = event.userName
        val host = event.host
        val offlineUUIDType = ExtraUuidUtils.matchType(uuid, name)

        val offlineHost = MixedLoginMain.getInstance().loginServerManager.shouldOfflineHost(host)
        if (offlineHost) {
            MixedLoginMain.getInstance().logger.info("匹配到离线host 玩家: $name")
        }
        MixedLoginMain.getInstance().logger.info("传入uuid信息 玩家: $name UUID:$uuid 类型: $offlineUUIDType")
        if (offlineUUIDType != OfflineUUIDType.UNKNOWN || offlineHost) {
            val serverId = MixedLoginMain.getInstance().loginServerManager.startOfflineRequest(name)
            event.serverId = serverId
            event.isOnline = false
        } else {
            event.isOnline = true
        }
    }
} 