package io.github.l1zerox.pandora

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

internal data class UpdateState(val checking: Boolean = false, val release: AppRelease? = null, val message: String = "")

internal object AppUpdates {
    val state=MutableStateFlow(UpdateState())
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var startupChecked=false
    fun check(manual: Boolean = false, beta: Boolean = BuildConfig.BETA, offer: Boolean = false) {
        if(state.value.checking || (!manual && startupChecked)) return
        startupChecked=true
        state.update { it.copy(checking=true) }
        scope.launch {
            try {
                val release=withContext(Dispatchers.IO) { fetchRelease(beta,offer) }
                state.value=UpdateState(release=release,message=if(manual && release == null) {
                    if(offer) "ダウンロード可能な${if(beta) "ベータ版" else "リリース版"}が見つかりませんでした。"
                    else "利用可能な新しいバージョンはありません。現在 ${BuildConfig.VERSION_NAME}"
                } else "")
                AppLog.info("バージョンチェック",release?.let { "ダウンロード可能なバージョン ${it.version}" } ?: "利用可能な更新はありません。")
            } catch(e: Exception) {
                if(e is CancellationException) throw e
                AppLog.info("バージョンチェック","確認できません: ${e.message}")
                state.value=UpdateState(message=if(manual) "更新を確認できませんでした。通信状態を確認して再度お試しください。" else "")
            }
        }
    }
    fun dismiss() { state.update { it.copy(release=null,message="") } }
    private fun fetchRelease(beta: Boolean, offer: Boolean): AppRelease? {
        val connection=URL("https://api.github.com/repos/L1ZeroX/youtube-pandora-android/releases?per_page=30").openConnection() as HttpURLConnection
        try {
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
