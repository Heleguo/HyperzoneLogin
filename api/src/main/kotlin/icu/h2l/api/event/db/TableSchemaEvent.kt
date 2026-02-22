package icu.h2l.api.event.db

enum class TableSchemaAction {
    CREATE_ALL,
    DROP_ALL,
}

data class TableSchemaEvent(
    val action: TableSchemaAction,
)
