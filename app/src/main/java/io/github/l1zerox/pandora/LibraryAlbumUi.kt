package io.github.l1zerox.pandora

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun LibraryAlbumTile(album: MusicAlbum, dark: Boolean, backdrop: LibraryBackdrop, onOpen: ()->Unit) {
    val context=LocalContext.current
    val cover=album.tracks.firstOrNull()
    val bitmap by produceState<Bitmap?>(null,cover?.uri) { value=null; if(cover != null) value=LibraryRepository.artwork(context,cover) }
    val palette=remember(bitmap,dark) { libraryPalette(bitmap?.let(::libraryThumbnailColor),dark) }
    Surface(onClick=onOpen,color=Color.Transparent,contentColor=palette.content,shape=RoundedCornerShape(16.dp),
        modifier=Modifier.fillMaxWidth().libraryCardGlass(palette,backdrop)) {
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center) {
                bitmap?.let { Image(it.asImageBitmap(),contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop) } ?: Text("♫")
            }
            Column(Modifier.weight(1f)) {
                Text(album.name,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                if(album.artist.isNotBlank()) Text(album.artist,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
                Text("${album.tracks.size}曲",style=MaterialTheme.typography.bodySmall)
            }
            Text("›")
        }
    }
}

@Composable
internal fun LibraryAlbumEditor(entries: List<LibraryEntry>, existing: UserPlaylist?, onSaved: (String)->Unit, onDismiss: ()->Unit) {
    val audio=remember(entries) { albumTracks(entries) }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var selected by rememberSaveable { mutableStateOf<List<String>>(ArrayList(existing?.uris.orEmpty())) }
    var search by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val shown=audio.filter { it.title.contains(search,true) || it.artist.contains(search,true) }
    fun toggle(uri: String) { selected=ArrayList(if(uri in selected) selected-uri else selected+uri) }
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(existing == null) "アルバムを追加" else "アルバムを編集")},text={
        LazyColumn(Modifier.heightIn(max=480.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item { OutlinedTextField(name,{name=it.take(60)},singleLine=true,label={Text("アルバム名")}) }
            item { OutlinedTextField(search,{search=it},singleLine=true,label={Text("曲を検索")}) }
            item { Text("${selected.count { uri -> audio.any { it.uri == uri } }}曲を選択") }
            item { TextButton(onClick={selected=ArrayList((selected+shown.map { it.uri }).distinct())}) { Text("表示中の曲をすべて選択") } }
            items(shown,key={it.uri}) { entry ->
                Row(Modifier.fillMaxWidth().clickable { toggle(entry.uri) },verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(entry.uri in selected,{toggle(entry.uri)})
                    Column(Modifier.weight(1f).padding(vertical=6.dp)) {
                        Text(entry.title,maxLines=2,overflow=TextOverflow.Ellipsis)
                        if(entry.artist.isNotBlank()) Text(entry.artist,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
            if(audio.isEmpty()) item { Text("先に「曲を取り込む」から音楽を追加してください。") }
            if(existing != null) item { TextButton(onClick={deleting=true}) { Text("アルバムを削除") } }
            if(error.isNotBlank()) item { Text(error,color=MaterialTheme.colorScheme.error) }
        }
    },confirmButton={TextButton(enabled=name.isNotBlank() && (existing != null || selected.isNotEmpty()),onClick={
        runCatching { LibraryExtras.saveAlbum(existing?.id,name,audio.filter { it.uri in selected }.map { it.uri }) }
            .onSuccess(onSaved).onFailure { error="アルバムを保存できませんでした。" }
    }) { Text("保存") }},dismissButton={TextButton(onClick=onDismiss) { Text("キャンセル") }})
    if(deleting && existing != null) AlertDialog(onDismissRequest={deleting=false},title={Text("アルバムを削除しますか？")},
        text={Text("曲のファイルは残ります。曲のアルバム情報があれば自動分類へ戻ります。")},
        confirmButton={TextButton(onClick={runCatching { LibraryExtras.deleteAlbum(existing.id) }.onSuccess { onSaved(MUSIC_ALBUM_ROOT) }
            .onFailure { error="削除できませんでした。" }; deleting=false}) { Text("削除") }},dismissButton={TextButton(onClick={deleting=false}) { Text("キャンセル") }})
}
