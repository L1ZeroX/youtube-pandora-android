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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class LibraryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by Store.state.collectAsStateWithLifecycle()
            SideEffect {
                WindowCompat.getInsetsController(window,window.decorView).apply {
                    isAppearanceLightStatusBars=!state.preferences.dark
                    isAppearanceLightNavigationBars=!state.preferences.dark
                }
            }
            MaterialTheme(colorScheme=if(state.preferences.dark) darkColorScheme() else lightColorScheme()) {
                LibraryScreen(state.preferences) { finish() }
            }
        }
    }
    override fun onStart() { super.onStart(); BubbleRuntime.enter(this) }
    override fun onResume() { super.onResume(); BubbleRuntime.sync(this) }
    override fun onStop() { super.onStop(); BubbleRuntime.leave(this) }
}

@Composable
internal fun LibraryScreen(preferences: Preferences, onBack: ()->Unit) {
    val dark=preferences.dark
    val wallpaper by rememberWallpaper(preferences.wallpaper)
    var wallpaperBounds by remember { mutableStateOf(Rect.Zero) }
    val frostedWallpaper by produceState<Bitmap?>(null,wallpaper,preferences.libraryBlur) {
        val image=wallpaper
        val strength=preferences.libraryBlur.coerceIn(0,20)
        value=if(image == null || strength == 0) image
            else withContext(Dispatchers.Default) { blurThumbnail(image,maxEdge=80,passes=strength) }
    }
    val backdrop=remember(frostedWallpaper,wallpaperBounds,preferences.opacity,dark,preferences.libraryGlass) {
        LibraryBackdrop(frostedWallpaper,wallpaperBounds,preferences.opacity,appBackgroundColor(dark),preferences.libraryGlass)
    }
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var entries by remember { mutableStateOf(emptyList<LibraryEntry>()) }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("すべて") }
    var search by rememberSaveable { mutableStateOf("") }
    var appearance by rememberSaveable { mutableStateOf(false) }
    val playback by PlaybackRuntime.state.collectAsStateWithLifecycle()
    val transport=rememberLibraryPlayback()
    fun openPlayer(uri: String? = null) {
        transport.handOffToPlayer()
        context.startActivity(Intent(context,PlayerActivity::class.java).apply { if(uri != null) putExtra("uri",uri) })
    }
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
    val playingEntry=entries.firstOrNull { it.uri == playback.uri }
    val playingArtwork by produceState<Bitmap?>(null,playingEntry?.uri) {
        value=null
        if(playingEntry != null) value=LibraryRepository.artwork(context,playingEntry)
    }
    val playingColor=remember(playingArtwork) { playingArtwork?.let { libraryThumbnailColor(it) } }
    val playingPalette=remember(playingColor,dark) { libraryPalette(playingColor,dark) }
    val colors=remember(playingColor,dark) { libraryColorScheme(playingColor,dark,appBackgroundColor(dark)) }
    MaterialTheme(colorScheme=colors) {
    Box(Modifier.fillMaxSize().onGloballyPositioned {
        val position=it.positionInWindow()
        wallpaperBounds=Rect(position.x,position.y,position.x+it.size.width,position.y+it.size.height)
    }) {
    WallpaperLayer(wallpaper,preferences,Modifier.fillMaxSize())
    Scaffold(containerColor=Color.Transparent) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal=16.dp)) {
            val hasWallpaper=wallpaper != null
            val headerTint=readableGlassTint(appSurfaceColor(dark)).let { it.copy(alpha=maxOf(it.alpha,1-preferences.glass)) }
            Surface(shape=RoundedCornerShape(20.dp),color=if(hasWallpaper) headerTint else colors.surface,
                contentColor=if(hasWallpaper) Color.White else colors.onSurface) {
            MaterialTheme(colorScheme=if(hasWallpaper) colors.copy(primary=Color.White,onSurface=Color.White,
                onSurfaceVariant=Color.White,outline=Color.White.copy(alpha=.7f)) else colors) {
            Column(Modifier.padding(12.dp)) {
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
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                listOf("すべて","動画","音楽").forEach { option -> FilterChip(selected=filter == option,onClick={filter=option},label={Text(option)}) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick={appearance=!appearance}) { Text(if(appearance) "調整終了" else "表示調整",fontSize=12.sp) }
            }
            if(playback.uri.isNotBlank()) Surface(color=Color.Transparent,contentColor=playingPalette.content,
                shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().libraryCardGlass(playingPalette,backdrop)) {
                Column(Modifier.padding(horizontal=12.dp,vertical=6.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(playback.title,Modifier.weight(1f).clickable { openPlayer() },maxLines=1,overflow=TextOverflow.Ellipsis)
                        LibraryPlayButton(playback.uri,transport,playingPalette) { openPlayer() }
                    }
                    if(transport.uri == playback.uri) LibrarySeekBar(playback.uri,transport,playingPalette,playingColor,backdrop)
                }
            }
            if(transport.error.isNotBlank()) Text(transport.error,fontSize=13.sp)
            if(error.isNotBlank()) Text(error,color=if(hasWallpaper) Color.White else MaterialTheme.colorScheme.error,fontSize=13.sp)
            if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(visible.isEmpty() && !loading) Text(if(entries.isEmpty()) "保存した動画・音楽がここに表示されます。" else "該当するファイルはありません。",Modifier.padding(vertical=24.dp))
            }
            }
            }
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(vertical=8.dp)) {
                if(appearance) item(key="library-appearance") {
                    Surface(shape=RoundedCornerShape(16.dp),color=if(hasWallpaper) headerTint else colors.surface,
                        contentColor=if(hasWallpaper) Color.White else colors.onSurface) {
                        Column(Modifier.padding(12.dp)) {
                            Text("動画・音楽パネル",fontWeight=FontWeight.Bold)
                            LibraryAppearanceControls(preferences,Store::preferences)
                        }
                    }
                }
                items(visible,key={it.uri}) { item ->
                    LibraryCard(item,dark,backdrop,transport) {
                        openPlayer(item.uri)
                    }
                }
            }
        }
    }
    }
    }
}

@Composable
internal fun LibraryCard(entry: LibraryEntry, dark: Boolean, backdrop: LibraryBackdrop, playback: LibraryPlayback, onClick: ()->Unit) {
    val context=LocalContext.current
    val bitmap by produceState<Bitmap?>(null,entry.uri) { value=null; value=LibraryRepository.artwork(context,entry) }
    val source=remember(bitmap) { bitmap?.let { libraryThumbnailColor(it) } }
    val palette=remember(source,dark) { libraryPalette(source,dark) }
    Surface(shape=RoundedCornerShape(16.dp),color=Color.Transparent,contentColor=palette.content,
        border=BorderStroke(1.dp,palette.content.copy(alpha=.25f)),
        modifier=Modifier.fillMaxWidth().libraryCardGlass(palette,backdrop).clickable(onClick=onClick)) {
        Column(Modifier.padding(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(80.dp).clip(RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center) {
                val image=bitmap
                if(image != null) Image(image.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                else Text(if(entry.video) "▶" else "♪",fontSize=32.sp,color=palette.content)
            }
            Column(Modifier.weight(1f)) {
                Text(entry.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold,color=palette.content)
                Text((if(entry.video) "動画" else "音楽") + if(entry.durationMs > 0) " · ${durationText(entry.durationMs/1000.0)}" else "",
                    fontSize=12.sp,color=palette.detail)
            }
            LibraryPlayButton(entry.uri,playback,palette,onClick)
        }
        if(playback.uri == entry.uri) LibrarySeekBar(entry.uri,playback,palette,source,backdrop)
        }
    }
}
