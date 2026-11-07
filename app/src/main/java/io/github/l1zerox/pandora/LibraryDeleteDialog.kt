package io.github.l1zerox.pandora

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

internal val LibraryEntrySaver=listSaver<LibraryEntry?,String>(
    save={ item -> if(item == null) emptyList() else listOf(item.uri,item.title,item.mime,item.durationMs.toString(),
        item.added.toString(),item.imported.toString(),item.width.toString(),item.height.toString(),item.autoDubbed.toString()) },
    restore={ if(it.isEmpty()) null else LibraryEntry(it[0],it[1],it[2],it[3].toLong(),it[4].toLong(),it[5].toBoolean(),it[6].toInt(),it[7].toInt(),it.getOrNull(8).toBoolean()) })

@Composable
internal fun LibraryDeleteDialog(entry: LibraryEntry, onDismiss: ()->Unit, onRemoved: ()->Unit) {
    val context=LocalContext.current.applicationContext
    val scope=rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var waiting by rememberSaveable { mutableStateOf(false) }
    var retry by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    fun failure(cause: Exception) {
        if(cause is CancellationException) throw cause
        AppLog.error("ライブラリー削除",cause)
        error="削除できませんでした。アクセス権を確認するか、端末のファイル管理アプリで削除してください。"
    }
    val consent=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        waiting=false
        if(result.resultCode == Activity.RESULT_OK) scope.launch {
            busy=true
            try {
                if(retry) check(LibraryDeletion.delete(context,entry,allowConsent=false) == LibraryDeleteResult.Deleted)
                else LibraryDeletion.finish(context,entry)
                onRemoved(); onDismiss()
            } catch(e: Exception) { failure(e) }
            finally { busy=false }
        }
    }
    val working=busy || waiting
    AlertDialog(onDismissRequest={if(!working) onDismiss()},title={Text("ファイルを削除しますか？")},
        text={Column {
            Text(entry.title)
            Text("保存元のファイルも削除します。この操作は元に戻せません。")
            if(entry.imported) Text("一覧から外すだけなら「ライブラリーから外す」を選んでください。")
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
            if(working) LinearProgressIndicator()
        }},
        confirmButton={TextButton(enabled=!working,onClick={scope.launch {
            busy=true; error=""
            try {
                when(val result=LibraryDeletion.delete(context,entry)) {
                    LibraryDeleteResult.Deleted -> { onRemoved(); onDismiss() }
                    is LibraryDeleteResult.Consent -> {
                        retry=result.retry; waiting=true
                        try { consent.launch(IntentSenderRequest.Builder(result.sender).build()) }
                        catch(e: Exception) { waiting=false; throw e }
                    }
                }
            } catch(e: Exception) { failure(e) }
            finally { busy=false }
        }}) { Text("削除") }},
        dismissButton={Column {
            TextButton(enabled=!working,onClick=onDismiss) { Text("キャンセル") }
            if(entry.imported) TextButton(enabled=!working,onClick={scope.launch {
                busy=true
                try { LibraryDeletion.removeImport(context,entry); onRemoved(); onDismiss() }
                catch(e: Exception) { failure(e) }
                finally { busy=false }
            }}) { Text("ライブラリーから外す") }
        }})
}
