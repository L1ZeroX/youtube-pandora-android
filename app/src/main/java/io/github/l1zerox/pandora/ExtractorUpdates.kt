package io.github.l1zerox.pandora

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object ExtractorUpdates {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var checker: ExtractorUpdateChecker?=null
    fun owner(context: Context): ExtractorUpdateChecker {
        val app=context.applicationContext
        return checker ?: ExtractorUpdateChecker(scope,SystemClock::elapsedRealtime) {
            withContext(Dispatchers.IO) {
                val installed=BundledExtractor.version(app)
                val current=if(shouldInstallExtractor(installed)) BUNDLED_EXTRACTOR_VERSION else requireNotNull(installed)
                val connection=URL("https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest").openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout=10_000; connection.readTimeout=10_000; connection.useCaches=false
                    connection.setRequestProperty("Accept","application/vnd.github+json")
                    connection.setRequestProperty("User-Agent","YoutubePandora-Android/${BuildConfig.VERSION_NAME}")
                    require(connection.responseCode == 200)
                    val bytes=connection.inputStream.use { it.readBytesLimited(1024*1024) }
                    current to requireNotNull(stableExtractorRelease(JSONObject(bytes.toString(Charsets.UTF_8))))
                } finally { connection.disconnect() }
            }
        }.also { checker=it }
    }
    fun start(context: Context) {
        if(Store.state.value.busy) return
        val owner=owner(context)
        if(owner.state.value.updating) return
        runCatching {
            ContextCompat.startForegroundService(context,Intent(context,DownloadService::class.java).setAction("UPDATE"))
            owner.started()
        }.onFailure { owner.completed(null,false,it.message.orEmpty()) }
    }
    fun finished(context: Context, successful: Boolean, message: String) {
        val app=context.applicationContext
        val owner=owner(app)
        scope.launch {
            val version=withContext(Dispatchers.IO) { BundledExtractor.version(app) }
            owner.completed(version,successful,message)
        }
    }
}

private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val result=java.io.ByteArrayOutputStream()
    val buffer=ByteArray(8192)
    while(true) {
        val n=read(buffer); if(n < 0) break
        require(result.size()+n <= limit)
        result.write(buffer,0,n)
    }
    return result.toByteArray()
}
