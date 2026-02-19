package icu.h2l.login.auth.online

import icu.h2l.login.auth.online.db.EntryTable

/**
 * auth-yggd 使用的数据库能力接口
 */
interface DatabaseManager {
    fun <T> executeTransaction(statement: () -> T): T

    fun getEntryTable(entryId: String): EntryTable?
}
