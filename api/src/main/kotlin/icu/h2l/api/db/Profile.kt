package icu.h2l.api.db

import java.util.*

/**
 * ProfileTable 对应的数据对象。
 */
data class Profile(
    val id: UUID,
    val name: String,
    val uuid: UUID
)
