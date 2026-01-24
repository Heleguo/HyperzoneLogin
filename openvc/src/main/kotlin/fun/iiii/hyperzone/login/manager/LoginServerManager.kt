package `fun`.iiii.hyperzone

import `fun`.iiii.hyperzone.login.HyperzoneLoginMain


class LoginServerManager {

    fun shouldOfflineHost(hostName: String): Boolean {
        if (hostName.isEmpty()) return false
        HyperzoneLoginMain.getConfig().hostMatch.start.forEach {
            if (hostName.startsWith(it)) return true
        }
        return false
    }

} 