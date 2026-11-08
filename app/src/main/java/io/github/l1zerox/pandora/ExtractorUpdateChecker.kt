package io.github.l1zerox.pandora

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

internal data class ExtractorUpdateState(val checking: Boolean = false, val current: String = "",
    val latest: String? = null, val message: String = "", val updating: Boolean = false)

internal fun stableExtractorRelease(json: JSONObject): String? = json.optString("tag_name")
    .takeIf { !json.optBoolean("draft") && !json.optBoolean("prerelease") && Regex("\\d{4}\\.\\d{2}\\.\\d{2}").matches(it) }

internal class ExtractorUpdateChecker(private val scope: CoroutineScope, private val now: ()->Long,
    private val fetch: suspend ()->Pair<String,String?>) {
    val state=MutableStateFlow(ExtractorUpdateState())
    private var nextCheck=0L
    fun check(manual: Boolean = false) {
        val old=state.value
        if(old.checking || old.updating || !manual && (now() < nextCheck || old.latest != null || old.message.isNotEmpty())) return
        state.update { it.copy(checking=true) }
        scope.launch {
            try {
                val (current,latest)=fetch()
                val available=latest?.takeIf { shouldInstallExtractor(current,it) }
                nextCheck=now()+6*60*60_000L
                state.value=ExtractorUpdateState(current=current,latest=available,
                    message=if(manual && available == null) "yt-dlpは最新です。現在 $current" else "")
            } catch(e: CancellationException) { state.update { it.copy(checking=false) }; throw e }
            catch(e: Exception) {
                nextCheck=now()+60_000L
                state.value=ExtractorUpdateState(message=if(manual) "yt-dlpの更新を確認できませんでした。通信状態を確認して再試行してください。" else "")
            }
        }
    }
    fun dismiss() { state.update { if(it.updating) it else it.copy(latest=null,message="") } }
    fun started() { state.update { it.copy(updating=true,message="") } }
    fun completed(installed: String?, successful: Boolean, detail: String) {
        val target=state.value.latest
        val verified=successful && installed != null && (target == null || !shouldInstallExtractor(installed,target))
        state.value=ExtractorUpdateState(current=installed.orEmpty(),message=if(verified) "yt-dlpを更新しました。現在 $installed"
            else if(successful) "yt-dlpの更新を完了できませんでした。更新後の版を再確認してください。現在 ${installed ?: "確認できません"}"
            else "yt-dlpの更新を完了できませんでした。${detail.take(300)}")
        nextCheck=now()+if(verified) 6*60*60_000L else 60_000L
    }
}
