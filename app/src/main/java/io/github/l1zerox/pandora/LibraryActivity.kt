package io.github.l1zerox.pandora

import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LibraryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(!BuildConfig.BETA) { finish(); return }
        enableEdgeToEdge()
        setContent {
            val state by Store.state.collectAsStateWithLifecycle()
            MaterialTheme(colorScheme=if(state.preferences.dark) darkColorScheme() else lightColorScheme()) {
                LibraryScreen { finish() }
            }
        }
    }
    override fun onStart() { super.onStart(); BubbleRuntime.enter(this) }
    override fun onResume() { super.onResume(); BubbleRuntime.sync(this) }
    override fun onStop() { super.onStop(); BubbleRuntime.leave(this) }
}

@Composable
internal fun LibraryScreen(onBack: ()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var entries by remember { mutableStateOf(emptyList<LibraryEntry>()) }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("すべて") }
    var search by rememberSaveable { mutableStateOf("") }
    val playback by PlaybackRuntime.state.collectAsStateWithLifecycle()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if(uris.isNotEmpty()) scope.launch {
            importing=true
            try {
                val failed=LibraryRepository.addFiles(context,uris)
                error=if(failed > 0) "${failed}件を追加できませんでした。端末に保存した動画・音楽を選んでください。" else ""
                refresh++
            } finally { importing=false }
        }
    }
    DisposableEffect(lifecycle,context) {
        val observer=object: ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { refresh++ }
        }
        context.contentResolver.registerContentObserver(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),true,observer)
        val resume=LifecycleEventObserver { _,event -> if(event == Lifecycle.Event.ON_START) refresh++ }
        lifecycle.addObserver(resume)
        onDispose { context.contentResolver.unregisterContentObserver(observer); lifecycle.removeObserver(resume) }
    }
    LaunchedEffect(refresh) {
        loading=true
        try { entries=LibraryRepository.load(context) }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { error="一覧を読み込めませんでした。「更新」で再試行してください。"; AppLog.error("ライブラリー",e) }
        finally { loading=false }
    }
    val visible=remember(entries,filter,search) { libraryEntries(entries,emptyList(),filter,search) }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal=16.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick=onBack) { Text("戻る") }
                Text("ライブラリー",Modifier.weight(1f),fontSize=24.sp,fontWeight=FontWeight.Bold)
                TextButton(onClick={error=""; refresh++},enabled=!loading) { Text("更新") }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text("保存した動画と音楽",fontSize=13.sp)
                OutlinedButton(onClick={picker.launch(arrayOf("video/*","audio/*"))},enabled=!importing) {
                    Text(if(importing) "追加中…" else "ファイルを追加")
                }
            }
            OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("タイトルで検索")})
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("すべて","動画","音楽").forEach { option -> FilterChip(selected=filter == option,onClick={filter=option},label={Text(option)}) }
            }
            if(playback.uri.isNotBlank()) OutlinedButton(onClick={context.startActivity(Intent(context,PlayerActivity::class.java))},modifier=Modifier.fillMaxWidth()) {
                Text("${if(playback.playing) "再生中" else "一時停止"} · ${playback.title}",maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error,fontSize=13.sp)
            if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(visible.isEmpty() && !loading) Text(if(entries.isEmpty()) "保存した動画・音楽がここに表示されます。" else "該当するファイルはありません。",Modifier.padding(vertical=24.dp))
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(vertical=8.dp)) {
                items(visible,key={it.uri}) { item ->
                    Surface(shape=RoundedCornerShape(16.dp),tonalElevation=2.dp,modifier=Modifier.fillMaxWidth().clickable {
                        context.startActivity(Intent(context,PlayerActivity::class.java).putExtra("uri",item.uri))
                    }) {
                        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            LibraryArtwork(item,Modifier.size(80.dp).clip(RoundedCornerShape(10.dp)))
                            Column(Modifier.weight(1f)) {
                                Text(item.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold)
                                Text((if(item.video) "動画" else "音楽") + if(item.durationMs > 0) " · ${durationText(item.durationMs/1000.0)}" else "",fontSize=12.sp)
                            }
                            Text("▶",fontSize=22.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryArtwork(entry: LibraryEntry, modifier: Modifier) {
    val context=LocalContext.current
    val bitmap by produceState<Bitmap?>(null,entry.uri) { value=LibraryRepository.artwork(context,entry) }
    Surface(modifier,color=MaterialTheme.colorScheme.secondaryContainer) {
        Box(contentAlignment=Alignment.Center) {
            val image=bitmap
            if(image != null) Image(image.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            else Text(if(entry.video) "▶" else "♪",fontSize=32.sp)
        }
    }
}
