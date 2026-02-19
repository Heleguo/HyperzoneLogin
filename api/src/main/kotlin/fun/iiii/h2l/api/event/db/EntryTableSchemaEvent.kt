package `fun`.iiii.h2l.api.event.db

import java.util.concurrent.CopyOnWriteArrayList

enum class EntryTableSchemaAction {
    CREATE_ALL,
    DROP_ALL,
}

data class EntryTableSchemaEvent(
    val action: EntryTableSchemaAction,
)

object EntryTableSchemaEventApi {
    private val listeners = CopyOnWriteArrayList<(EntryTableSchemaEvent) -> Unit>()

    @JvmStatic
    fun registerListener(listener: (EntryTableSchemaEvent) -> Unit) {
        listeners.add(listener)
    }

    @JvmStatic
    fun fire(event: EntryTableSchemaEvent) {
        listeners.forEach { it(event) }
    }
}
