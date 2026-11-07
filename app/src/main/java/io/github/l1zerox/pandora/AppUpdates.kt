package io.github.l1zerox.pandora

import kotlinx.coroutines.*
import android.os.SystemClock
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

internal object AppUpdates {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val checker=UpdateChecker(scope,BuildConfig.VERSION_NAME,SystemClock::elapsedRealtime,
        fetch={ beta,offer -> withContext(Dispatchers.IO) { fetchRelease(beta,offer) } },
        log={ AppLog.info("バージョンチェック",it) })
    val state get()=checker.state
    fun check(manual: Boolean = false, beta: Boolean = BuildConfig.BETA, offer: Boolean = false) {
        checker.check(manual,beta,offer)
    }
    fun retryFailedCheck() { if(state.value.failed) check() }
    fun dismiss() = checker.dismiss()
    private fun fetchRelease(beta: Boolean, offer: Boolean): AppRelease? {
        val connection=URL("https://api.github.com/repos/L1ZeroX/youtube-pandora-android/releases?per_page=30").openConnection() as HttpURLConnection
        try {
            connection.useCaches=false
            connection.connectTimeout=10_000; connection.readTimeout=10_000
            connection.setRequestProperty("Accept","application/vnd.github+json")
            connection.setRequestProperty("User-Agent","YoutubePandora-Android/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("X-GitHub-Api-Version","2022-11-28")
            require(connection.responseCode == 200) { "GitHub HTTP ${connection.responseCode}" }
            val limit=2*1024*1024
            val bytes=connection.inputStream.use { input ->
                val output=java.io.ByteArrayOutputStream()
                val buffer=ByteArray(8192)
                while(true) {
                    val count=input.read(buffer)
                    if(count < 0) break
                    require(output.size()+count <= limit) { "バージョン情報が大きすぎます。" }
                    output.write(buffer,0,count)
                }
                output.toByteArray()
            }
            val array=JSONArray(bytes.toString(Charsets.UTF_8))
            val releases=(0 until array.length()).mapNotNull { index ->
                val item=array.optJSONObject(index) ?: return@mapNotNull null
                val assets=item.optJSONArray("assets") ?: JSONArray()
                ReleaseCandidate(item.optString("tag_name"),item.optBoolean("draft"),item.optBoolean("prerelease"),item.optString("body"),
                    (0 until assets.length()).mapNotNull { i ->
                        assets.optJSONObject(i)?.let { ReleaseAsset(it.optString("name"),it.optString("browser_download_url"),it.optString("state") == "uploaded") }
                    })
            }
            return newestRelease(releases,BuildConfig.VERSION_NAME,beta,offer,unifiedOnly=true)
        } finally { connection.disconnect() }
    }
}
