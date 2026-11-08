package io.github.l1zerox.pandora

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

internal data class BatchDeletionState(val id: String, val entries: List<LibraryEntry>, val index: Int = 0,
    val deleted: Set<String> = emptySet(), val failed: Int = 0, val consent: LibraryDeleteResult.Consent? = null,
    val responding: Boolean = false) {
    val finished get()=index >= entries.size
    fun completed(success: Boolean): BatchDeletionState {
        check(!finished)
        return copy(index=index+1,consent=null,responding=false,
            deleted=if(success) deleted+entries[index].uri else deleted,failed=failed+if(success) 0 else 1)
    }
}

/** Application-scoped so rotation never repeats a confirmed deletion or loses an Android consent result. */
internal object LibraryBatchDeletion {
    val state=MutableStateFlow<BatchDeletionState?>(null)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private lateinit var context: Application
    fun start(context: Context, entries: List<LibraryEntry>) {
        check(state.value == null)
        this.context=context.applicationContext as Application
        state.value=BatchDeletionState(UUID.randomUUID().toString(),entries.distinctBy { it.uri })
        scope.launch { advance() }
    }
    private suspend fun advance() {
        while(true) {
            val current=state.value ?: return
            if(current.finished || current.consent != null) return
            val item=current.entries[current.index]
            try {
                when(val result=LibraryDeletion.delete(context,item)) {
                    LibraryDeleteResult.Deleted -> complete(true)
                    is LibraryDeleteResult.Consent -> { state.value=current.copy(consent=result); return }
                }
            } catch(e: Exception) { AppLog.error("一括削除",e); complete(false) }
        }
    }
    private fun complete(deleted: Boolean) {
        val current=state.value ?: return
        state.value=current.completed(deleted)
    }
    fun consentResult(approved: Boolean) {
        val current=state.value ?: return
        val consent=current.consent ?: return
        if(current.responding) return
        state.value=current.copy(responding=true)
        scope.launch {
            var success=false
            if(approved) try {
                val entry=current.entries[current.index]
                if(consent.retry) check(LibraryDeletion.delete(context,entry,false) == LibraryDeleteResult.Deleted)
                else LibraryDeletion.finish(context,entry)
                success=true
            } catch(e: Exception) { AppLog.error("一括削除の許可",e) }
            complete(success); advance()
        }
    }
    fun dismiss() { if(state.value?.finished == true) state.value=null }
}

@Composable
internal fun BatchDeletionProgress() {
    val task by LibraryBatchDeletion.state.collectAsStateWithLifecycle()
    val current=task ?: return
    var launched by rememberSaveable { mutableStateOf("") }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        LibraryBatchDeletion.consentResult(it.resultCode == Activity.RESULT_OK)
    }
    val request="${current.id}:${current.index}"
    LaunchedEffect(request,current.consent) {
        val consent=current.consent
        if(consent != null && launched != request && !current.responding) {
            launched=request
            try { launcher.launch(IntentSenderRequest.Builder(consent.sender).build()) }
            catch(e: Exception) { AppLog.error("一括削除の確認",e); LibraryBatchDeletion.consentResult(false) }
        }
    }
    AlertDialog(onDismissRequest={LibraryBatchDeletion.dismiss()},title={Text(if(current.finished) "削除の結果" else "削除中…")},
        text={Column {
            Text("削除 ${current.deleted.size}件 / 削除できなかった項目 ${current.failed}件")
            if(!current.finished) { Text("${current.index+1} / ${current.entries.size}件"); LinearProgressIndicator() }
            if(current.failed > 0) Text("未許可・削除できなかったファイルは一覧に残しています。")
        }},confirmButton={TextButton(enabled=current.finished,onClick={LibraryBatchDeletion.dismiss()}) { Text("閉じる") }})
}
