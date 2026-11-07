package io.github.l1zerox.pandora

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    fun choose(mode: String) {
        if(Store.state.value.busy) return
        Store.preferences(Store.state.value.preferences.copy(artworkMode=mode))
        error=""
    }
    fun save(bitmap: Bitmap, crop: ArtworkCrop) {
        if(Store.state.value.busy || loading) return
        scope.launch {
            loading=true; error=""
            var target: File? = null
            var accepted=false
            try {
                withContext(Dispatchers.IO) { target=saveCroppedArtwork(context,bitmap,crop) }
                check(!Store.state.value.busy) { "処理が始まったため変更できません。完了後に画像を選んでください。" }
                val previous=Store.state.value.preferences.artworkImage
                Store.preferences(Store.state.value.preferences.copy(artworkMode="ギャラリー",artworkImage=target!!.absolutePath))
                accepted=true
                pendingUri=null
                if(previous.isNotBlank()) File(previous).takeIf {
                    it.parentFile?.canonicalFile == File(context.filesDir,"artwork").canonicalFile
                }?.delete()
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { error=e.message ?: "画像を読み込めませんでした。" }
            finally { if(!accepted) target?.delete(); loading=false }
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) {
            if(Store.state.value.busy) error="処理中は画像を変更できません。完了後に選んでください。"
            else { error=""; pendingUri=uri.toString() }
        }
    }
    Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(10.dp)) {
        Text("アルバムアート · 選択中: ${p.artworkMode}",Modifier.padding(horizontal=12.dp,vertical=8.dp),
            color=MaterialTheme.colorScheme.onPrimaryContainer,fontSize=14.sp,fontWeight=FontWeight.Bold)
    }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        FilterChip(selected=p.artworkMode == "サムネイル",onClick={choose("サムネイル")},enabled=!busy && !loading,
            modifier=Modifier.weight(1f),label={Text("サムネイル")},leadingIcon=if(p.artworkMode == "サムネイル") ({Text("✓")}) else null)
        FilterChip(selected=p.artworkMode == "ギャラリー",onClick={picker.launch(arrayOf("image/*"))},enabled=!busy && !loading,
            modifier=Modifier.weight(1f),label={Text("ギャラリーから選ぶ")},leadingIcon=if(p.artworkMode == "ギャラリー") ({Text("✓")}) else null)
    }
    if(p.artworkMode == "ギャラリー" && p.artworkImage.isNotBlank()) {
        AsyncImage(model=File(p.artworkImage),contentDescription="選択したアルバムアート",
            modifier=Modifier.fillMaxWidth().height(140.dp).border(2.dp,MaterialTheme.colorScheme.primary,RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp)).padding(6.dp),contentScale=ContentScale.Fit)
    }
    FilterChip(selected=p.artworkMode == "なし",onClick={choose("なし")},enabled=!busy && !loading,
        label={Text("アートワークなし")},leadingIcon=if(p.artworkMode == "なし") ({Text("✓")}) else null)
    Text(if(loading) "画像を保存中…" else "ギャラリー画像は位置・拡大を調整して正方形に切り抜けます。サムネイルは動画ごと、選んだ画像は次に保存するMP3すべてに適用します。",fontSize=12.sp)
    if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
    pendingUri?.let { uri -> key(uri) {
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        var decoding by remember { mutableStateOf(true) }
        LaunchedEffect(uri) {
            try { bitmap=withContext(Dispatchers.IO) { loadArtworkBitmap(context,Uri.parse(uri)) } }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { error=e.message ?: "画像を読み込めませんでした。" }
            finally { decoding=false }
        }
        ArtworkCropDialog(bitmap,decoding,loading,busy,error,
            onDismiss={pendingUri=null; error=""},onSave={ crop -> bitmap?.let { save(it,crop) } })
    } }
}
