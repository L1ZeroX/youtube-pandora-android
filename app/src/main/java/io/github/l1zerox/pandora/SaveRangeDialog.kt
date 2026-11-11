package io.github.l1zerox.pandora

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SaveRangeDialog(initial: SaveRange, editable: Boolean, onApply: (SaveRange)->Unit, onDismiss: ()->Unit,
    videos: List<Video> = emptyList(), resolving: Boolean = false, onResolve: ((Video)->Unit)? = null) {
    var enabled by rememberSaveable { mutableStateOf(initial.enabled) }
    var start by rememberSaveable { mutableStateOf(saveTimeText(initial.startSeconds)) }
    var end by rememberSaveable { mutableStateOf(initial.endSeconds?.let(::saveTimeText).orEmpty()) }
    var error by rememberSaveable { mutableStateOf("") }
    var referenceKey by rememberSaveable { mutableStateOf("") }
    var choosingReference by remember { mutableStateOf(false) }
    val references=saveRangeBarReferences(videos)
    val reference=references.firstOrNull { it.key == referenceKey } ?: references.firstOrNull()
    val maximum=saveRangeBarMaximum(reference?.duration)
    val unresolved=saveRangeBarCandidates(videos).firstOrNull { saveRangeBarMaximum(it.duration) == null }
    var requested by rememberSaveable { mutableStateOf(false) }
    fun resolve() {
        if(unresolved != null && onResolve != null) {
            requested=true; error=""
            runCatching { onResolve(unresolved) }.onFailure { error="動画の長さを取得できませんでした。再試行してください。" }
        }
    }
    LaunchedEffect(maximum,editable,unresolved?.key) {
        if(maximum == null && editable && !requested) resolve()
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text("保存範囲")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("時間を指定",Modifier.weight(1f))
                Switch(checked=enabled,onCheckedChange={
                    enabled=it; error=""
                    if(it && maximum != null && end.isBlank() && (start.isBlank() || parseSaveTime(start) == 0L))
                        end=saveTimeText(maximum)
                },enabled=editable,
                    modifier=Modifier.semantics { contentDescription="保存範囲を指定" })
            }
                if(reference != null && maximum != null) {
                    Text("バーで範囲を選択",fontSize=14.sp)
                    if(references.size > 1) Box {
                        OutlinedButton(onClick={choosingReference=true},enabled=editable,modifier=Modifier.fillMaxWidth()) {
                            Text("基準: ${reference.title} ▾",maxLines=2,overflow=TextOverflow.Ellipsis)
                        }
                        DropdownMenu(expanded=choosingReference,onDismissRequest={choosingReference=false}) {
                            references.forEach { video -> DropdownMenuItem(text={
                                Text("${video.title} · ${durationText(video.duration)}",maxLines=2,overflow=TextOverflow.Ellipsis)
                            },onClick={referenceKey=video.key; choosingReference=false},enabled=editable) }
                        }
                    } else Text(reference.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=12.sp)
                    val selection=saveRangeBarSelection(start,end,maximum)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("開始 ${saveTimeText(selection.start)}",fontSize=12.sp)
                        Text(if(end.isBlank()) "終了 最後まで" else "終了 ${saveTimeText(selection.end)}",fontSize=12.sp)
                    }
                    key(reference.key,maximum) {
                        SaveRangeSlider(selection,maximum,editable && enabled) { changed ->
                            start=saveTimeText(changed.start)
                            end=changed.endText(selection,end)
                            error=""
                        }
                    }
                    Text("左右のつまみで1秒単位に指定。バーは基準動画の長さまで表示します。",fontSize=12.sp)
                } else {
                    Text("バーで範囲を選択",fontSize=14.sp)
                    SaveRangeSlider(SaveRangeBarSelection(0,1),1,false) {}
                    Text(if(resolving) "動画の長さを取得しています…" else if(videos.isEmpty()) "リンクを追加するとバーで指定できます。"
                        else "長さの取得後にバーを操作できます。時刻の手入力も使えます。",fontSize=12.sp)
                    if(resolving) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(onResolve != null && unresolved != null) TextButton(onClick={resolve()},enabled=editable) { Text("動画の長さを取得") }
                }
            if(enabled) {
                OutlinedTextField(value=start,onValueChange={start=it.take(24); error=""},label={Text("開始時刻")},
                    placeholder={Text("0:30")},singleLine=true,enabled=editable,modifier=Modifier.fillMaxWidth(),
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii))
                OutlinedTextField(value=end,onValueChange={end=it.take(24); error=""},label={Text("終了時刻")},
                    placeholder={Text("空欄で最後まで")},singleLine=true,enabled=editable,modifier=Modifier.fillMaxWidth(),
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii))
                Text("例: 30、1:30、1:02:15。終了は動画の長さまでに調整します。",fontSize=12.sp)
                Text("選択した動画・音楽に同じ範囲を適用します。取得後に切り出すため、ダウンロード量は減りません。",fontSize=12.sp)
            } else Text("動画・音楽を全体で保存します。")
            if(error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error,fontSize=13.sp)
            if(!editable && !resolving) Text("ダウンロード・一時停止中は変更できません。",fontSize=12.sp)
        }
    },confirmButton={TextButton(enabled=editable,onClick={
        runCatching {
            val changed=if(enabled) saveRangeFromInput(true,start,end) else initial.copy(enabled=false)
            onApply(changed)
        }.onFailure { error=it.message ?: "時刻を確認してください。" }
    }) { Text("適用") }},dismissButton={TextButton(onClick=onDismiss) { Text("キャンセル") }})
}
