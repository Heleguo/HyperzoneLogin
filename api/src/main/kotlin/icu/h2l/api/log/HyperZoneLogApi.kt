package icu.h2l.api.log

interface HyperZoneLogger {
    fun info(message: String)
    fun debug(message: String)
    fun warn(message: String)
    fun error(message: String, throwable: Throwable? = null)
}

object HyperZoneLogApi {
    @Volatile
    private var logger: HyperZoneLogger = NoopHyperZoneLogger

    @JvmStatic
    fun registerLogger(newLogger: HyperZoneLogger) {
        logger = newLogger
    }

    @JvmStatic
    fun getLogger(): HyperZoneLogger = logger
}

private object NoopHyperZoneLogger : HyperZoneLogger {
    override fun info(message: String) = Unit
    override fun debug(message: String) = Unit
    override fun warn(message: String) = Unit
    override fun error(message: String, throwable: Throwable?) = Unit
}

inline fun info(block: () -> String) {
    HyperZoneLogApi.getLogger().info(block())
}

inline fun debug(block: () -> String) {
    HyperZoneLogApi.getLogger().debug(block())
}

inline fun warn(block: () -> String) {
    HyperZoneLogApi.getLogger().warn(block())
}

inline fun error(throwable: Throwable? = null, block: () -> String) {
    HyperZoneLogApi.getLogger().error(block(), throwable)
}