package `fun`.iiii.hyperzone.login.listener

import com.velocitypowered.api.event.Subscribe
import `fun`.iiii.hyperzone.login.HyperzoneLoginMain
import `fun`.iiii.hyperzone.login.type.OfflineUUIDType
import `fun`.iiii.hyperzone.login.util.ExtraUuidUtils
import `fun`.iiii.openvelocity.api.event.connection.OnlineAuthEvent
import `fun`.iiii.openvelocity.api.event.connection.OpenPreLoginEvent

class EventListener {
    @Subscribe
    fun onPreLogin(event: OpenPreLoginEvent) {
        val uuid = event.uuid
        val name = event.userName
        val host = event.host
        val offlineUUIDType = ExtraUuidUtils.matchType(uuid, name)

        val offlineHost = HyperzoneLoginMain.getInstance().loginServerManager.shouldOfflineHost(host)
        if (offlineHost) {
            HyperzoneLoginMain.getInstance().logger.info("匹配到离线host 玩家: $name")
        }
        HyperzoneLoginMain.getInstance().logger.info("传入uuid信息 玩家: $name UUID:$uuid 类型: $offlineUUIDType")
        if (offlineUUIDType != OfflineUUIDType.UNKNOWN || offlineHost) {
            event.isOnline = false
        } else {
            event.isOnline = true
        }
    }

    @Subscribe
    fun onPreLogin(event: OnlineAuthEvent) {
//        测试
        HyperzoneLoginMain.getInstance().logger.info("已跳过登入")
        event.isSuccess = true
    }
} 