package icu.h2l.login.merge.service

import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MergeAmLogWriter(logPath: Path) : AutoCloseable {
    private val writer: BufferedWriter = Files.newBufferedWriter(
        logPath,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE
    )

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun log(message: String) {
        writer.write("[${LocalDateTime.now().format(dateFormat)}] $message")
        writer.newLine()
        writer.flush()
    }

    override fun close() {
        writer.close()
    }
}
