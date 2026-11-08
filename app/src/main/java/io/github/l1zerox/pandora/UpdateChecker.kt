package io.github.l1zerox.pandora

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class UpdateState(val checking: Boolean = false, val release: AppRelease? = null,
    val message: String = "", val failed: Boolean = false)

/** Main-thread owner. A service keeping the process alive must not disable future checks. */
internal class UpdateChecker(
    private val scope: CoroutineScope,
    private val currentVersion: String,
    private val now: ()->Long,
    private val fetch: suspend (Boolean,Boolean)->AppRelease?,
    private val log: (String)->Unit = {},
) {
    val state=MutableStateFlow(UpdateState())
    private var nextAutomaticCheck=0L

    fun check(manual: Boolean = false, beta: Boolean = false, offer: Boolean = false) {
        val previous=state.value
        if(previous.checking || (!manual && (now() < nextAutomaticCheck || previous.release != null || previous.message.isNotEmpty()))) return
        state.update { it.copy(checking=true,failed=false) }
        scope.launch {
            try {
                val release=fetch(beta,offer)
                nextAutomaticCheck=now()+5*60_000L
                state.value=UpdateState(release=release,message=if(manual && release == null) {
                    if(offer && !beta && ReleaseVersion.parse(currentVersion)?.beta != null)
                        "現在のベータ版から上書きできるリリース版はまだありません。新しいリリース版をお待ちください。"
                    else if(offer) "ダウンロード可能な${if(beta) "ベータ版" else "リリース版"}が見つかりませんでした。"
                    else "利用可能な新しいバージョンはありません。現在 $currentVersion"
                } else "")
                log(release?.let { "ダウンロード可能なバージョン ${it.version}" } ?: "利用可能な更新はありません。")
            } catch(e: CancellationException) {
                state.update { it.copy(checking=false) }
                throw e
            } catch(e: Exception) {
                nextAutomaticCheck=now()+30_000L
                state.value=UpdateState(failed=true,message=if(manual) "更新を確認できませんでした。通信状態を確認して再度お試しください。" else "")
                log("確認できません: ${e.message}")
            }
        }
    }

    fun dismiss() { state.update { it.copy(release=null,message="") } }
}
