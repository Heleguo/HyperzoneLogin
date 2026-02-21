package icu.h2l.login.database

/**
 * 数据库配置
 */
data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val driverClassName: String = "com.mysql.cj.jdbc.Driver",
    val tablePrefix: String = "",
    val maximumPoolSize: Int = 10,
    val minimumIdle: Int = 2,
    val connectionTimeout: Long = 30000,
    val idleTimeout: Long = 600000,
    val maxLifetime: Long = 1800000
) {
    companion object {
        /**
         * 创建 MySQL 配置
         */
        fun mysql(
            host: String,
            port: Int = 3306,
            database: String,
            username: String,
            password: String,
            tablePrefix: String = "",
            parameters: String = "useSSL=false&serverTimezone=UTC&characterEncoding=utf8",
            driverClassName: String = "com.mysql.cj.jdbc.Driver",
            maximumPoolSize: Int = 10,
            minimumIdle: Int = 2,
            connectionTimeout: Long = 30000,
            idleTimeout: Long = 600000,
            maxLifetime: Long = 1800000
        ) = DatabaseConfig(
            jdbcUrl = "jdbc:mysql://$host:$port/$database?$parameters",
            username = username,
            password = password,
            driverClassName = driverClassName,
            tablePrefix = tablePrefix,
            maximumPoolSize = maximumPoolSize,
            minimumIdle = minimumIdle,
            connectionTimeout = connectionTimeout,
            idleTimeout = idleTimeout,
            maxLifetime = maxLifetime
        )

        /**
         * 创建 MariaDB 配置
         */
        fun mariadb(
            host: String,
            port: Int = 3306,
            database: String,
            username: String,
            password: String,
            tablePrefix: String = "",
            parameters: String = "useSSL=false&characterEncoding=utf8",
            driverClassName: String = "org.mariadb.jdbc.Driver",
            maximumPoolSize: Int = 10,
            minimumIdle: Int = 2,
            connectionTimeout: Long = 30000,
            idleTimeout: Long = 600000,
            maxLifetime: Long = 1800000
        ) = DatabaseConfig(
            jdbcUrl = "jdbc:mariadb://$host:$port/$database?$parameters",
            username = username,
            password = password,
            driverClassName = driverClassName,
            tablePrefix = tablePrefix,
            maximumPoolSize = maximumPoolSize,
            minimumIdle = minimumIdle,
            connectionTimeout = connectionTimeout,
            idleTimeout = idleTimeout,
            maxLifetime = maxLifetime
        )
        
        /**
         * 创建 H2 配置（用于测试）
         */
        fun h2(
            path: String = "./data/hyperzone_login",
            tablePrefix: String = "",
            maximumPoolSize: Int = 10,
            minimumIdle: Int = 2,
            connectionTimeout: Long = 30000,
            idleTimeout: Long = 600000,
            maxLifetime: Long = 1800000
        ) = DatabaseConfig(
            jdbcUrl = "jdbc:h2:file:$path;MODE=MySQL",
            username = "sa",
            password = "",
            driverClassName = "org.h2.Driver",
            tablePrefix = tablePrefix,
            maximumPoolSize = maximumPoolSize,
            minimumIdle = minimumIdle,
            connectionTimeout = connectionTimeout,
            idleTimeout = idleTimeout,
            maxLifetime = maxLifetime
        )
        
        /**
         * 创建 SQLite 配置（推荐用于单机部署）
         * SQLite 使用单线程模式避免并发问题
         */
        fun sqlite(
            path: String = "./data/hyperzone_login.db",
            tablePrefix: String = "",
            maximumPoolSize: Int = 10,
            minimumIdle: Int = 2,
            connectionTimeout: Long = 30000,
            idleTimeout: Long = 600000,
            maxLifetime: Long = 1800000
        ) = DatabaseConfig(
            jdbcUrl = "jdbc:sqlite:$path?journal_mode=WAL&busy_timeout=30000",
            username = "",
            password = "",
            driverClassName = "org.sqlite.JDBC",
            tablePrefix = tablePrefix,
            // SQLite 必须使用单连接避免并发问题
            maximumPoolSize = 1,
            minimumIdle = 1,
            connectionTimeout = connectionTimeout,
            idleTimeout = idleTimeout,
            maxLifetime = maxLifetime
        )
    }
}
