package io.github.l1zerox.pandora

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun LibraryFolderDialog(collection: String, onSelect: (String)->Unit, onDismiss: ()->Unit,
    moving: List<String> = emptyList(), onMoved: ()->Unit = {}) {
    val data by LibraryExtras.state.collectAsStateWithLifecycle()
    var create by rememberSaveable { mutableStateOf(false) }
    var rename by rememberSaveable { mutableStateOf("") }
    var deleting by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    fun choose(id: String) {
        runCatching {
            if(moving.isNotEmpty()) { LibraryExtras.moveToFolder(id,moving); onMoved() }
            else { onSelect(id); onDismiss() }
        }.onFailure { error="フォルダーを更新できませんでした。" }
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(moving.isEmpty()) "フォルダー" else "${moving.size}件をフォルダーへ移動")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("ライブラリー内で整理します。保存元のファイルの場所は変わりません。")
            if(moving.isEmpty()) TextButton(onClick={choose("")}) { Text("すべてのファイル") }
            TextButton(onClick={choose(if(moving.isEmpty()) "unfiled" else "")}) {
                Text(if(moving.isEmpty()) "未分類" else "フォルダーから外す（未分類へ）")
            }
            data.folders.forEach { folder -> key(folder.id) {
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick={choose(folder.id)},modifier=Modifier.weight(1f)) {
                        Text("📁 ${folder.name}${if(collection == folder.id) " ✓" else ""}")
                    }
                    if(moving.isEmpty()) {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick={menu=true}) { Text("⋮") }
                            DropdownMenu(menu,{menu=false}) {
                                DropdownMenuItem(text={Text("名前を変更")},onClick={menu=false; rename=folder.id})
                                DropdownMenuItem(text={Text("フォルダーを削除")},onClick={menu=false; deleting=folder.id})
                            }
                        }
                    }
                }
            } }
            TextButton(onClick={create=true}) { Text("＋ フォルダーを作成") }
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={TextButton(onClick=onDismiss) { Text("閉じる") }})
    if(create || rename.isNotBlank()) PlaylistNameDialog(data.folders.firstOrNull { it.id == rename }?.name.orEmpty(),
        onDismiss={create=false; rename=""},onSave={ name ->
            if(create) {
                val id=LibraryExtras.createFolder(name,moving)
                if(moving.isNotEmpty()) onMoved() else { onSelect(id); onDismiss() }
            } else LibraryExtras.renameFolder(rename,name)
            create=false; rename=""
        },kind="フォルダー")
    if(deleting.isNotBlank()) AlertDialog(onDismissRequest={deleting=""},title={Text("フォルダーを削除しますか？")},
        text={Text("中の動画・音楽は削除せず、未分類へ戻します。")},confirmButton={TextButton(onClick={
            runCatching { LibraryExtras.deleteFolder(deleting) }.onSuccess {
                if(collection == deleting) onSelect("unfiled")
            }.onFailure { error="フォルダーを削除できませんでした。" }
            deleting=""
        }) { Text("フォルダーを削除") }},dismissButton={TextButton(onClick={deleting=""}) { Text("キャンセル") }})
}
