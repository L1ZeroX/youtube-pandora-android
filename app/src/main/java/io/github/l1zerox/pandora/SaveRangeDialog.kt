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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SaveRangeDialog(initial: SaveRange, editable: Boolean, onApply: (SaveRange)->Unit, onDismiss: ()->Unit) {
    var enabled by rememberSaveable { mutableStateOf(initial.enabled) }
    var start by rememberSaveable { mutableStateOf(saveTimeText(initial.startSeconds)) }
    var end by rememberSaveable { mutableStateOf(initial.endSeconds?.let(::saveTimeText).orEmpty()) }
    var error by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("保存範囲")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("時間を指定",Modifier.weight(1f))
                Switch(checked=enabled,onCheckedChange={enabled=it; error=""},enabled=editable,
                    modifier=Modifier.semantics { contentDescription="保存範囲を指定" })
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
            if(!editable) Text("ダウンロード・一時停止中は変更できません。",fontSize=12.sp)
        }
    },confirmButton={TextButton(enabled=editable,onClick={
        runCatching {
            val changed=if(enabled) saveRangeFromInput(true,start,end) else initial.copy(enabled=false)
            onApply(changed)
        }.onFailure { error=it.message ?: "時刻を確認してください。" }
    }) { Text("適用") }},dismissButton={TextButton(onClick=onDismiss) { Text("キャンセル") }})
}
