package icu.h2l.login.merge.service

import icu.h2l.login.merge.config.MergeAmConfig
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager


data class AuthMeRow(
    val username: String,
    val realName: String?,
    val password: String?
)

class AmSourceReader(
    private val dataDirectory: Path,
    private val config: MergeAmConfig
) {
    fun readAuthMeRows(): List<AuthMeRow> {
        connect().use { connection ->
            return readAuthMeRows(connection)
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

            "SQLITE", "SQLITE_DB" -> {
                Class.forName("org.sqlite.JDBC")
                val sqlite = source.sqlite
                val url = if (sqlite.jdbcUrl.isNotBlank()) {
                    sqlite.jdbcUrl
                } else {
                    val configuredPath = Paths.get(sqlite.path)
                    val absolutePath = if (configuredPath.isAbsolute) {
                        configuredPath.normalize()
                    } else {
                        dataDirectory.toAbsolutePath().normalize().resolve(configuredPath).normalize()
                    }
                    val dbPath = absolutePath.toString().replace('\\', '/')
                    val extraParams = sqlite.parameters.trim()
                    if (extraParams.isBlank()) {
                        "jdbc:sqlite:$dbPath"
                    } else {
                        "jdbc:sqlite:$dbPath?$extraParams"
                    }
                }
                DriverManager.getConnection(url)
            }

            else -> {
                throw IllegalArgumentException("不支持的源数据库类型: ${source.type}")
            }
        }
    }

    private fun readAuthMeRows(connection: Connection): List<AuthMeRow> {
        val sql = "SELECT username, realname, password FROM ${config.tables.authMeTable}"
        connection.prepareStatement(sql).use { statement ->
            statement.executeQuery().use { rs ->
                val result = mutableListOf<AuthMeRow>()
                while (rs.next()) {
                    result.add(
                        AuthMeRow(
                            username = rs.getString("username") ?: "",
                            realName = rs.getString("realname"),
                            password = rs.getString("password")
                        )
                    )
                }
                return result
            }
        }
    }
}
