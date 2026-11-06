package io.github.l1zerox.pandora

import java.io.File
import java.net.URI
import java.util.Base64

data class LogEntry(val time: Long, val level: String, val stage: String, val message: String)

internal fun cleanLog(value: String, maxCharacters: Int = 4000, keepStart: Boolean = false): String {
    val urls = Regex("https?://[^\\s\"<>]+",RegexOption.IGNORE_CASE)
    val headers = Regex("(?im)\\b(authorization|cookie|set-cookie|password|access_token|refresh_token|token|signature)\\s*[:=]\\s*[^\\r\\n]+")
    val sanitized = headers.replace(urls.replace(value) {
        val host = runCatching { URI(it.value).host }.getOrNull()
        if(host == null) "[URL非表示]" else "https://$host/[URL非表示]"
    },"$1=[非表示]").replace(Regex("\\u001B\\[[0-9;]*[A-Za-z]"),"")
    return if(keepStart) sanitized.take(maxCharacters) else sanitized.takeLast(maxCharacters)
}

/** Two bounded, private journal files. No Android APIs, so persistence can be tested on the JVM. */
internal class LogJournal(directory: File, private val maxBytes: Int = 128 * 1024) {
    private val current = File(directory,"current.log")
    private val previous = File(directory,"previous.log")
    init { directory.mkdirs() }
    fun append(entry: LogEntry) {
        val encoded = Base64.getEncoder().encodeToString(entry.message.toByteArray(Charsets.UTF_8))
        val line = "${entry.time}\t${entry.level}\t${entry.stage}\t$encoded\n"
        if(current.length() + line.toByteArray(Charsets.UTF_8).size > maxBytes && current.exists()) {
            current.copyTo(previous,overwrite=true)
            current.writeText("")
        }
        current.appendText(line,Charsets.UTF_8)
    }
    fun read(): List<LogEntry> = listOf(previous,current).flatMap { file ->
        if(!file.exists()) emptyList() else file.readLines(Charsets.UTF_8).mapNotNull { line ->
            runCatching {
                val parts = line.split('\t',limit=4)
                require(parts.size == 4 && parts[1] in listOf("INFO","ERROR","WARN"))
                LogEntry(parts[0].toLong(),parts[1],parts[2],String(Base64.getDecoder().decode(parts[3]),Charsets.UTF_8))
            }.getOrNull()
        }
    }.takeLast(300)
    fun clear() { listOf(current,previous).forEach { check(!it.exists() || it.delete()) { "ログを消去できません。" } } }
}
