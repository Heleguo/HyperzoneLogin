package `fun`.iiii.h2l.api.db

interface HyperZoneTransactionExecutor {
    fun <T> execute(statement: () -> T): T
}

object HyperZoneTransactionApi {
    @Volatile
    private var executor: HyperZoneTransactionExecutor = NoopHyperZoneTransactionExecutor

    @JvmStatic
    fun registerExecutor(newExecutor: HyperZoneTransactionExecutor) {
        executor = newExecutor
    }

    @JvmStatic
    fun getExecutor(): HyperZoneTransactionExecutor = executor

    @JvmStatic
    fun <T> execute(statement: () -> T): T = executor.execute(statement)
}

private object NoopHyperZoneTransactionExecutor : HyperZoneTransactionExecutor {
    override fun <T> execute(statement: () -> T): T {
        throw IllegalStateException("HyperZoneTransactionApi executor is not registered")
    }
}
