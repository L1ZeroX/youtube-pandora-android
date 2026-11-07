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
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryScreen(preferences: Preferences, onBack: ()->Unit) {
    val dark=preferences.dark
    val layout=LibraryLayout.fromId(preferences.libraryLayout)
    val gridState=rememberLazyGridState()
    val wallpaper by rememberWallpaper(preferences.wallpaper)
    val wallpaperAccent=remember(wallpaper,dark) { readableBadgeColor(wallpaper?.let { accentColor(it,dark) } ?: Color(0xFF4388FF)) }
    val headerAccent by animateColorAsState(wallpaperAccent,label="library-header-accent")
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
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val searchFocus=remember { FocusRequester() }
    val focusManager=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    var appearance by rememberSaveable { mutableStateOf(false) }
    var deletion by rememberSaveable(stateSaver=LibraryEntrySaver) { mutableStateOf<LibraryEntry?>(null) }
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
            LibraryHeader(headerAccent,loading,onBack,onRefresh={error=""; refresh++})
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text("保存した動画と音楽",fontSize=13.sp)
                OutlinedButton(onClick={picker.launch(arrayOf("video/*","audio/*"))},enabled=!importing) {
                    Text(if(importing) "追加中…" else "ファイルを追加")
                }
            }
            if(searchExpanded) {
                OutlinedTextField(search,{search=it},Modifier.fillMaxWidth().focusRequester(searchFocus),singleLine=true,
                    label={Text("タイトルで検索")},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
                    keyboardActions=KeyboardActions(onSearch={focusManager.clearFocus(); keyboard?.hide()}))
                LaunchedEffect(Unit) { if(!appearance) { searchFocus.requestFocus(); keyboard?.show() } }
            }
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("すべて","動画","音楽","ショート").forEach { option -> FilterChip(selected=filter == option,onClick={filter=option},label={Text(option)}) }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FlowRow(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick={
                        if(searchExpanded) { search=""; focusManager.clearFocus(); keyboard?.hide() }
                        searchExpanded=!searchExpanded
                    }) { Text(if(searchExpanded) "検索を閉じる" else "検索 🔎") }
                }
                LibraryAppearanceButton(headerAccent) { focusManager.clearFocus(); keyboard?.hide(); appearance=true }
                LibraryLayoutButton(layout,headerAccent,onOpen={focusManager.clearFocus(); keyboard?.hide()},
                    onSelect={Store.preferences(preferences.copy(libraryLayout=it.id))})
            }
            if(playback.uri.isNotBlank()) Surface(color=Color.Transparent,contentColor=playingPalette.content,
                shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().libraryCardGlass(playingPalette,backdrop)) {
                Column(Modifier.padding(horizontal=12.dp,vertical=6.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(playback.title,Modifier.weight(1f).clickable { openPlayer() },maxLines=1,overflow=TextOverflow.Ellipsis)
                        LibraryPlayButton(playback.uri,transport,playingPalette) { openPlayer() }
                    }
                    if(transport.uri == playback.uri) LibrarySeekBar(playback.uri,transport,playingPalette,playingColor,backdrop,compact=true)
                }
            }
            if(transport.error.isNotBlank()) Text(transport.error,fontSize=13.sp)
            if(error.isNotBlank()) Text(error,color=if(hasWallpaper) Color.White else MaterialTheme.colorScheme.error,fontSize=13.sp)
            if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(visible.isEmpty() && !loading) Text(if(entries.isEmpty()) "保存した動画・音楽がここに表示されます。" else "該当するファイルはありません。",Modifier.padding(vertical=24.dp))
            }
            }
            }
            LazyVerticalGrid(columns=GridCells.Fixed(layout.columns),state=gridState,modifier=Modifier.weight(1f),
                horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(vertical=8.dp)) {
                items(visible,key={it.uri}) { item ->
                    LibraryCard(item,dark,backdrop,transport,layout,onDelete={if(!importing) deletion=item}) {
                        openPlayer(item.uri)
                    }
                }
            }
        }
    }
    }
    if(appearance) LibraryAppearanceDialog(wallpaper,preferences,playingEntry ?: visible.firstOrNull(),headerAccent,colors,
        onChange=Store::preferences,onDismiss={appearance=false},previewEntries=visible.take(6))
    UpdatePrompt(allowed=!appearance && deletion == null && !importing && error.isBlank())
    deletion?.let { entry -> key(entry.uri) {
        LibraryDeleteDialog(entry,onDismiss={deletion=null},onRemoved={
            entries=entries.filterNot { it.uri == entry.uri }; refresh++
        })
    } }
    }
}

@Composable
internal fun LibraryCard(entry: LibraryEntry, dark: Boolean, backdrop: LibraryBackdrop, playback: LibraryPlayback,
    layout: LibraryLayout = LibraryLayout.STANDARD, onDelete: ()->Unit, onClick: ()->Unit) {
    val context=LocalContext.current
    val bitmap by produceState<Bitmap?>(null,entry.uri) { value=null; value=LibraryRepository.artwork(context,entry) }
    val source=remember(bitmap) { bitmap?.let { libraryThumbnailColor(it) } }
    val palette=remember(source,dark) { libraryPalette(source,dark) }
    var menu by remember { mutableStateOf(false) }
    Surface(shape=RoundedCornerShape(16.dp),color=Color.Transparent,contentColor=palette.content,
        border=BorderStroke(1.dp,palette.content.copy(alpha=.25f)),
        modifier=Modifier.fillMaxWidth().libraryCardGlass(palette,backdrop).clickable(onClick=onClick)) {
        LibraryCardBody(entry,bitmap,palette,layout,
            play={LibraryPlayButton(entry.uri,playback,palette,onClick)},menu={
                Box {
                    IconButton(onClick={menu=true},modifier=Modifier.semantics { contentDescription="${entry.title}のメニュー" }) {
                        Text("⋮",fontSize=22.sp,color=palette.content)
                    }
                    DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                        DropdownMenuItem(text={Text("削除")},onClick={menu=false; onDelete()})
                    }
                }
            },footer={if(playback.uri == entry.uri) LibrarySeekBar(entry.uri,playback,palette,source,backdrop,compact=layout != LibraryLayout.STANDARD)})
    }
}
