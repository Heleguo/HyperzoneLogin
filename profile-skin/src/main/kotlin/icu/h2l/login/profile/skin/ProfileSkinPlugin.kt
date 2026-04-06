package icu.h2l.login.profile.skin

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import icu.h2l.login.HyperZoneLoginMain

@Plugin(id = "hzl-profile-skin", name = "HyperZoneLogin - Profile Skin")
class ProfileSkinPlugin @Inject constructor(private val server: ProxyServer) {
    private val logger = java.util.logging.Logger.getLogger("hzl-profile-skin")

    @Subscribe
    fun onEnable(@Suppress("UNUSED_PARAMETER") event: ProxyInitializeEvent) {
        val mainPluginPresent = server.pluginManager.getPlugin("hyperzonelogin").isPresent
        if (mainPluginPresent) {
            try {
                HyperZoneLoginMain.getInstance().registerModule(ProfileSkinSubModule())
            } catch (t: Throwable) {
                logger.warning("Failed to register ProfileSkinSubModule: ${t.message}")
            }
        } else {
            logger.warning("HyperZoneLogin main plugin not found; ProfileSkinSubModule will wait until available.")
        }
    }
}

