package `fun`.iiii.hyperzone.login.util

import `fun`.iiii.hyperzone.login.HyperzoneLoginMain
import `fun`.iiii.hyperzone.login.type.OfflineUUIDType
import `fun`.iiii.hyperzone.login.util.uuid.PCL2UUIDUtil
import java.nio.charset.StandardCharsets
import java.util.*

object ExtraUuidUtils {
    private val zero: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    fun matchType(holderUUID: UUID?, name: String): OfflineUUIDType {
        if (holderUUID == null) {
            return OfflineUUIDType.ZERO
        }
        return when {
            HyperzoneLoginMain.getConfig().uuidMatch.offline &&holderUUID == getNormalOfflineUUID(name) -> OfflineUUIDType.OFFLINE
            HyperzoneLoginMain.getConfig().uuidMatch.pcl2.enable && PCL2UUIDUtil.isPCL2UUID(
                holderUUID,
                name
            ) -> OfflineUUIDType.PCL

            HyperzoneLoginMain.getConfig().uuidMatch.zero && holderUUID == zero -> OfflineUUIDType.ZERO

            else -> OfflineUUIDType.UNKNOWN
        }
    }

    private fun getNormalOfflineUUID(username: String): UUID {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:$username").toByteArray(StandardCharsets.UTF_8))
    }

} 