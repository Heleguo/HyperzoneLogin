package icu.h2l.login.merge.service

import icu.h2l.login.merge.config.MergeMlConfig
import java.nio.ByteBuffer
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*

data class MlInGameProfileRow(
    val inGameUuid: UUID,
    val currentUsernameLowerCase: String?,
    val currentUsernameOriginal: String?
)

data class MlUserDataRow(
    val onlineUuid: UUID,
    val onlineName: String?,
    val serviceId: Int,
    val inGameProfileUuid: UUID?,
    val whitelist: Boolean
)

class MlSourceReader(
    private val dataDirectory: Path,
    private val config: MergeMlConfig
) {
    fun readProfilesAndUserData(): Pair<List<MlInGameProfileRow>, List<MlUserDataRow>> {
        connect().use { connection ->
            return readProfiles(connection) to readUserData(connection)
        }
    }

    private fun connect(): Connection {
        val source = config.source
        return when (source.type.uppercase()) {
            "MYSQL" -> {
                Class.forName("com.mysql.cj.jdbc.Driver")
                val mysql = source.mysql
                val url = "jdbc:mysql://${mysql.host}:${mysql.port}/${mysql.database}?${mysql.parameters}"
                DriverManager.getConnection(url, mysql.username, mysql.password)
            }
            "H2DB", "H2" -> {
                Class.forName("org.h2.Driver")
                val h2 = source.h2
                val url = if (h2.jdbcUrl.isNotBlank()) {
                    h2.jdbcUrl
                } else {
                    val normalizedPath = h2.path.removeSuffix(".mv.db")
                    val configuredPath = Paths.get(normalizedPath)
                    val absolutePath = if (configuredPath.isAbsolute) {
                        configuredPath.normalize()
                    } else {
                        dataDirectory.toAbsolutePath().normalize().resolve(configuredPath).normalize()
                    }
                    val dbPath = absolutePath.toString().replace('\\', '/')
                    "jdbc:h2:file:$dbPath;${h2.parameters}"
                }

                try {
                    DriverManager.getConnection(url, h2.username, h2.password)
                } catch (ex: SQLException) {
                    if (ex.message?.contains("90048") == true) {
                        throw IllegalStateException(
                            "检测到 H2 数据库版本不兼容（90048）。当前驱动无法直接读取该 .mv.db，请先用旧版 H2 导出脚本再导入到 2.x，或在 merge-ml.conf 的 source.h2.jdbcUrl 填入可访问的兼容数据源 URL。原始错误: ${ex.message}",
                            ex
                        )
                    }
                    throw ex
                }
            }
            else -> {
                throw IllegalArgumentException("不支持的源数据库类型: ${source.type}")
            }
        }
    }

    private fun readProfiles(connection: Connection): List<MlInGameProfileRow> {
        val sql = "SELECT in_game_uuid, current_username_lower_case, current_username_original FROM ${config.tables.inGameProfileTable}"
        connection.prepareStatement(sql).use { statement ->
            statement.executeQuery().use { rs ->
                val result = mutableListOf<MlInGameProfileRow>()
                while (rs.next()) {
                    val inGameUuid = bytesToUuid(rs.getBytes("in_game_uuid")) ?: continue
                    result.add(
                        MlInGameProfileRow(
                            inGameUuid = inGameUuid,
                            currentUsernameLowerCase = rs.getString("current_username_lower_case"),
                            currentUsernameOriginal = rs.getString("current_username_original")
                        )
                    )
                }
                return result
            }
        }
    }

    private fun readUserData(connection: Connection): List<MlUserDataRow> {
        val sql = "SELECT online_uuid, online_name, service_id, in_game_profile_uuid, whitelist FROM ${config.tables.userDataTable}"
        connection.prepareStatement(sql).use { statement ->
            statement.executeQuery().use { rs ->
                val result = mutableListOf<MlUserDataRow>()
                while (rs.next()) {
                    val onlineUuid = bytesToUuid(rs.getBytes("online_uuid")) ?: continue
                    result.add(
                        MlUserDataRow(
                            onlineUuid = onlineUuid,
                            onlineName = rs.getString("online_name"),
                            serviceId = rs.getInt("service_id"),
                            inGameProfileUuid = bytesToUuid(rs.getBytes("in_game_profile_uuid")),
                            whitelist = rs.getBoolean("whitelist")
                        )
                    )
                }
                return result
            }
        }
    }

    private fun bytesToUuid(bytes: ByteArray?): UUID? {
        if (bytes == null || bytes.size != 16) {
            return null
        }
        val buffer = ByteBuffer.wrap(bytes)
        return UUID(buffer.long, buffer.long)
    }
}
