package icu.h2l.login.merge.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
class MergeAmConfig {
    @Comment("源数据库配置")
    var source: SourceConfig = SourceConfig()

    @Comment("源表配置")
    var tables: SourceTables = SourceTables()

    @ConfigSerializable
    class SourceConfig {
        @Comment("源库类型，支持 SQLITE 或 MYSQL")
        var type: String = "SQLITE"

        @Comment("SQLite 配置")
        var sqlite: SqliteConfig = SqliteConfig()

        @Comment("MySQL 配置")
        var mysql: MysqlConfig = MysqlConfig()
    }

    @ConfigSerializable
    class SqliteConfig {
        @Comment("可选：直接指定 JDBC URL。留空时按 path + parameters 生成")
        var jdbcUrl: String = ""

        @Comment("SQLite 文件路径（相对于插件数据目录）")
        var path: String = "merge/authme.db"

        @Comment("SQLite JDBC 附加参数")
        var parameters: String = ""
    }

    @ConfigSerializable
    class MysqlConfig {
        @Comment("MySQL 地址")
        var host: String = "127.0.0.1"

        @Comment("MySQL 端口")
        var port: Int = 3306

        @Comment("数据库名")
        var database: String = "authme"

        @Comment("用户名")
        var username: String = "root"

        @Comment("密码")
        var password: String = "password"

        @Comment("JDBC 参数")
        var parameters: String = "useSSL=false&serverTimezone=UTC&characterEncoding=utf8"
    }

    @ConfigSerializable
    class SourceTables {
        @Comment("AuthMe 数据表名")
        var authMeTable: String = "authme"
    }
}
