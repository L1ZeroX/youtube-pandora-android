package io.github.l1zerox.pandora

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.*
import java.io.File

@Composable
internal fun LibraryArtworkEditor(target: String, title: String, entry: LibraryEntry?, album: String? = null, onDismiss: ()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var pending by rememberSaveable(target) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val current by rememberLibraryArtwork(entry,album)
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            pending=uri.toString(); error=""
        }
    }
    if(pending == null) AlertDialog(onDismissRequest=onDismiss,title={Text("アートワークを変更")},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(title)
            current?.let { Image(it.asImageBitmap(),"現在のアートワーク",Modifier.fillMaxWidth().height(180.dp),contentScale=ContentScale.Fit) }
            Button(onClick={picker.launch(arrayOf("image/*"))},enabled=!saving) { Text("画像を選んでトリミング") }
            OutlinedButton(onClick={
                scope.launch { saving=true; error=""
                    try { withContext(Dispatchers.IO) { LibraryArtwork.set(target,null) }; onDismiss() }
                    catch(e: CancellationException) { throw e }
                    catch(e: Exception) { error="元のアートワークに戻せませんでした。" }
                    finally { saving=false }
                }
            },enabled=!saving) { Text("変更を解除") }
            Text("このアプリ内の表示に適用します。音楽ファイル本体は変更しません。",style=MaterialTheme.typography.bodySmall)
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={TextButton(onClick=onDismiss,enabled=!saving) { Text("閉じる") }})
    pending?.let { uri -> key(target,uri) {
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        var loading by remember { mutableStateOf(true) }
        LaunchedEffect(uri) {
            try { bitmap=withContext(Dispatchers.IO) { loadArtworkBitmap(context,uri.toUri()) } }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { error="画像を読み込めませんでした。別の画像を選んでください。" }
            finally { loading=false }
        }
        ArtworkCropDialog(bitmap,loading,saving,false,error,onDismiss={pending=null; error=""},onSave={ crop ->
            val source=bitmap
            if(source != null && !saving) scope.launch {
                saving=true; error=""; var image: File?=null; var accepted=false
                try {
                    withContext(Dispatchers.IO) { image=saveCroppedArtwork(context,source,crop); LibraryArtwork.set(target,image); accepted=true }
                    pending=null; onDismiss()
                } catch(e: CancellationException) { throw e }
                catch(e: Exception) { error="アートワークを保存できませんでした。" }
                finally { if(!accepted) image?.delete(); saving=false }
            }
        })
    } }
}
