package io.github.l1zerox.pandora

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import java.io.File

@Composable
internal fun ArtworkSettings(p: Preferences, busy: Boolean) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    fun choose(mode: String) {
        if(Store.state.value.busy) return
        Store.preferences(Store.state.value.preferences.copy(artworkMode=mode))
        error=""
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) scope.launch {
            loading=true; error=""
            var target: File? = null
            var accepted=false
            try {
                withContext(Dispatchers.IO) { target=importArtwork(context,uri) }
                check(!Store.state.value.busy) { "処理が始まったため変更できません。完了後に画像を選んでください。" }
                val previous=Store.state.value.preferences.artworkImage
                Store.preferences(Store.state.value.preferences.copy(artworkMode="ギャラリー",artworkImage=target!!.absolutePath))
                accepted=true
                if(previous.isNotBlank()) File(previous).takeIf {
                    it.parentFile?.canonicalFile == File(context.filesDir,"artwork").canonicalFile
                }?.delete()
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { error=e.message ?: "画像を読み込めませんでした。" }
            finally { if(!accepted) target?.delete(); loading=false }
        }
    }
    Text("アルバムアート · ${p.artworkMode}",fontSize=14.sp)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick={choose("サムネイル")},enabled=!busy && !loading,modifier=Modifier.weight(1f)) { Text("サムネイル") }
        OutlinedButton(onClick={picker.launch(arrayOf("image/*"))},enabled=!busy && !loading,modifier=Modifier.weight(1f)) { Text("ギャラリーから選ぶ") }
    }
    if(p.artworkMode == "ギャラリー" && p.artworkImage.isNotBlank()) {
        AsyncImage(model=File(p.artworkImage),contentDescription="選択したアルバムアート",
            modifier=Modifier.fillMaxWidth().height(100.dp),contentScale=ContentScale.Fit)
    }
    TextButton(onClick={choose("なし")},enabled=!busy && !loading) { Text("アートワークなし") }
    Text(if(loading) "画像を読み込み中…" else "サムネイルは動画ごと、選んだ画像は次に保存するMP3すべてに適用します。サムネイルがない動画は画像なしで保存します。",fontSize=12.sp)
    if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
}
