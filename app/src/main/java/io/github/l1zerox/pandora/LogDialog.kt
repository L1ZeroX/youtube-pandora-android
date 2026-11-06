package io.github.l1zerox.pandora

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogFileProvider : FileProvider()

@Composable
fun LogDialog(onDismiss: ()->Unit) {
    val snapshot by AppLog.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var errorsOnly by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    var sharing by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val clock = remember { SimpleDateFormat("HH:mm:ss",Locale.US) }
    val entries = snapshot.entries.filter { !errorsOnly || it.level == "ERROR" }.asReversed()
    AlertDialog(onDismissRequest=onDismiss,title={Text("処理ログ")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("記録内のエラー ${snapshot.errors} 件",color=if(snapshot.errors > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("エラーのみ",Modifier.weight(1f)); Switch(errorsOnly,{errorsOnly=it})
            }
            Text("新しい順。URLの詳細・認証情報は伏せて記録します。",fontSize=11.sp)
            if(snapshot.storageWarning.isNotBlank()) Text(snapshot.storageWarning,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
            LazyColumn(Modifier.fillMaxWidth().heightIn(min=120.dp,max=300.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                if(entries.isEmpty()) item { Text(if(errorsOnly) "記録内にエラーはありません。" else "ログはまだありません。") }
                items(entries) { entry ->
                    SelectionContainer { Column {
                        Text("${clock.format(Date(entry.time))} [${entry.level}] ${entry.stage}",fontSize=12.sp,
                            color=if(entry.level == "ERROR") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Text(entry.message,fontSize=11.sp)
                    } }
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={clipboard.setText(AnnotatedString(AppLog.text(errorsOnly)));feedback="コピーしました。"}) { Text("コピー") }
                OutlinedButton(enabled=!sharing,onClick={
                    sharing=true
                    val text=AppLog.text(errorsOnly)
                    scope.launch {
                        try {
                            val file=withContext(Dispatchers.IO) {
                                val folder=File(context.cacheDir,"log-exports").apply { mkdirs() }
                                File(folder,"YoutubePandora-log.txt").apply { writeText(text,Charsets.UTF_8) }
                            }
                            val uri=FileProvider.getUriForFile(context,"${context.packageName}.logs",file)
                            val intent=Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            intent.clipData=ClipData.newRawUri("処理ログ",uri)
                            context.startActivity(Intent.createChooser(intent,"ログを共有"))
                        } catch(e: Exception) { feedback="共有できません: ${e.message}"; AppLog.error("ログ共有",e) }
                        finally { sharing=false }
                    }
                }) { Text("共有") }
            }
            if(feedback.isNotBlank()) Text(feedback,fontSize=12.sp)
        }
    },confirmButton={TextButton(onClick=onDismiss) { Text("閉じる") }},dismissButton={TextButton(onClick={confirmClear=true}) { Text("ログを消去") }})
    if(confirmClear) AlertDialog(onDismissRequest={confirmClear=false},title={Text("ログを消去しますか？")},
        text={Text("記録済みのログを消去します。動画ファイルは残ります。")},
        confirmButton={TextButton(onClick={AppLog.clear();confirmClear=false;feedback=""}) { Text("消去") }},
        dismissButton={TextButton(onClick={confirmClear=false}) { Text("キャンセル") }})
}
