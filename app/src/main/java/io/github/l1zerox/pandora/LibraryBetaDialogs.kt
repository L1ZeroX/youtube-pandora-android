package io.github.l1zerox.pandora

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun LibraryOrganizeDialog(collection: String, sort: LibrarySort, dubbed: Boolean,
    onCollection: (String)->Unit, onSort: (LibrarySort)->Unit, onDubbed: (Boolean)->Unit, onDismiss: ()->Unit) {
    val data by LibraryExtras.state.collectAsStateWithLifecycle()
    var newList by rememberSaveable { mutableStateOf(false) }
    var rename by rememberSaveable { mutableStateOf("") }
    var remove by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("ライブラリーを整理")},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("表示するリスト",style=MaterialTheme.typography.titleSmall)
            (listOf("" to "すべて","favorites" to "♥ お気に入り")+data.playlists.map { it.id to it.name }).forEach { (id,name) ->
                Row(Modifier.fillMaxWidth().clickable { onCollection(id) },verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=collection == id,onClick={onCollection(id)})
                    Text(name,Modifier.weight(1f))
                    if(id.isNotBlank() && id != "favorites") {
                        var menu by remember(id) { mutableStateOf(false) }
                        Box {
                            TextButton(onClick={menu=true}) { Text("⋮") }
                            DropdownMenu(menu,{menu=false}) {
                                DropdownMenuItem(text={Text("名前を変更")},onClick={menu=false; rename=id})
                                DropdownMenuItem(text={Text("リストを削除")},onClick={menu=false; remove=id})
                            }
                        }
                    }
                }
            }
            TextButton(onClick={newList=true}) { Text("＋ プレイリストを作成") }
            HorizontalDivider()
            Text("並び順",style=MaterialTheme.typography.titleSmall,modifier=Modifier.padding(top=12.dp))
            LibrarySort.entries.forEach { option ->
                Row(Modifier.fillMaxWidth().clickable { onSort(option) },verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=sort == option,onClick={onSort(option)})
                    Text(if(collection.isNotBlank() && collection != "favorites" && option == LibrarySort.NEWEST) "リストの登録順" else option.label)
                }
            }
            Row(Modifier.fillMaxWidth().clickable { onDubbed(!dubbed) },verticalAlignment=Alignment.CenterVertically) {
                Checkbox(checked=dubbed,onCheckedChange=onDubbed)
                Text("オートダビング版のみ")
            }
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={TextButton(onClick=onDismiss) { Text("完了") }})
    if(newList || rename.isNotBlank()) PlaylistNameDialog(data.playlists.firstOrNull { it.id == rename }?.name.orEmpty(),
        onDismiss={newList=false; rename=""},onSave={name ->
            if(newList) onCollection(LibraryExtras.create(name)) else LibraryExtras.rename(rename,name)
            newList=false; rename=""
        })
    if(remove.isNotBlank()) AlertDialog(onDismissRequest={remove=""},title={Text("プレイリストを削除しますか？")},
        text={Text("動画・音楽のファイルは残ります。")},confirmButton={TextButton(onClick={
            runCatching { LibraryExtras.deletePlaylist(remove) }.onSuccess {
                if(collection == remove) onCollection(""); remove=""
            }.onFailure { error="リストを削除できませんでした。"; remove="" }
        }) { Text("リストを削除") }},dismissButton={TextButton(onClick={remove=""}) { Text("キャンセル") }})
}

@Composable
private fun PlaylistNameDialog(initial: String, onDismiss: ()->Unit, onSave: (String)->Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(initial.isBlank()) "プレイリストを作成" else "名前を変更")},
        text={Column {
            OutlinedTextField(name,{name=it.take(60)},singleLine=true,label={Text("リスト名")})
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        }},confirmButton={TextButton(enabled=name.isNotBlank(),onClick={
            runCatching { onSave(name) }.onFailure { error="保存できませんでした。もう一度お試しください。" }
        }) { Text("保存") }},dismissButton={TextButton(onClick=onDismiss) { Text("キャンセル") }})
}

@Composable
internal fun AddToPlaylistDialog(uris: List<String>, onDismiss: ()->Unit, onAdded: ()->Unit) {
    val data by LibraryExtras.state.collectAsStateWithLifecycle()
    var create by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("${uris.size}件をプレイリストに追加")},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            data.playlists.forEach { list ->
                TextButton(onClick={
                    runCatching { LibraryExtras.add(list.id,uris) }.onSuccess { onAdded() }
                        .onFailure { error="追加できませんでした。" }
                },modifier=Modifier.fillMaxWidth()) { Text(list.name) }
            }
            TextButton(onClick={create=true}) { Text("＋ 新しいプレイリスト") }
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={TextButton(onClick=onDismiss) { Text("閉じる") }})
    if(create) PlaylistNameDialog("",onDismiss={create=false},onSave={
        LibraryExtras.create(it,uris); create=false; onAdded()
    })
}

@Composable
internal fun ResumePlaybackDialog(entry: LibraryEntry, position: Long, onStart: (Long)->Unit, onDismiss: ()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text("続きから再生しますか？")},
        text={Text("${entry.title}\n前回の位置 ${durationText(position/1000.0)}")},
        confirmButton={TextButton(onClick={onStart(position)}) { Text("続きから") }},
        dismissButton={TextButton(onClick={onStart(0)}) { Text("最初から") }})
}
