package io.github.l1zerox.pandora

import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
    var importMenu by rememberSaveable { mutableStateOf(false) }
    var importNotice by remember { mutableStateOf("") }
    var importedCount by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("すべて") }
    var sectionName by rememberSaveable { mutableStateOf(LibrarySection.VIDEO.name) }
    val section=LibrarySection.valueOf(sectionName)
    var pickerSection by rememberSaveable { mutableStateOf(sectionName) }
    var albumEditor by rememberSaveable { mutableStateOf(false) }
    var artworkTarget by rememberSaveable { mutableStateOf("") }
    var artworkTitle by rememberSaveable { mutableStateOf("") }
    var artworkAlbum by rememberSaveable { mutableStateOf<String?>(null) }
    var artworkTrack by rememberSaveable { mutableStateOf<String?>(null) }
    fun editArtwork(entry: LibraryEntry?, album: MusicAlbum? = null) {
        artworkTarget=album?.let { albumArtworkKey(it.id) } ?: entry?.let { trackArtworkKey(it.uri) }.orEmpty()
        artworkTitle=album?.name ?: entry?.title.orEmpty(); artworkAlbum=album?.id; artworkTrack=entry?.uri
    }
    var editingAlbum by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var controlsExpanded by rememberSaveable { mutableStateOf(false) }
    val searchFocus=remember { FocusRequester() }
    val focusManager=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    var appearance by rememberSaveable { mutableStateOf(false) }
    var deletion by rememberSaveable(stateSaver=LibraryEntrySaver) { mutableStateOf<LibraryEntry?>(null) }
    val playback by PlaybackRuntime.state.collectAsStateWithLifecycle()
    val transport=rememberLibraryPlayback()
    val collections by LibraryExtras.state.collectAsStateWithLifecycle()
    val batch by LibraryBatchDeletion.state.collectAsStateWithLifecycle()
    var folderDialog by rememberSaveable { mutableStateOf(false) }
    var moveToFolder by rememberSaveable { mutableStateOf<List<String>>(arrayListOf()) }
    var collection by rememberSaveable { mutableStateOf("") }
    val folderBrowsing=isFolderCollection(collection)
    val folderRoot=collection == LIBRARY_FOLDER_ROOT
    val albumBrowsing=isAlbumCollection(collection)
    val albumRoot=collection == MUSIC_ALBUM_ROOT
    var sortName by rememberSaveable { mutableStateOf(LibrarySort.NEWEST.name) }
    var indexLabel by remember { mutableStateOf<String?>(null) }
    val sort=LibrarySort.valueOf(sortName)
    var dubbedOnly by rememberSaveable { mutableStateOf(false) }
    var organize by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var addToList by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var confirmBatch by rememberSaveable { mutableStateOf(false) }
    var resumeEntry by rememberSaveable(stateSaver=LibraryEntrySaver) { mutableStateOf<LibraryEntry?>(null) }
    fun navigateCollection(id: String) {
        collection=id; selected=arrayListOf(); selecting=false
        if(isFolderCollection(id) || isAlbumCollection(id)) controlsExpanded=false
        focusManager.clearFocus(); keyboard?.hide()
    }
    fun navigateBack() {
        if(folderBrowsing) navigateCollection(folderParent(collection))
        else if(albumBrowsing) navigateCollection(if(albumRoot) "" else MUSIC_ALBUM_ROOT) else onBack()
    }
    BackHandler(enabled=(folderBrowsing || albumBrowsing) && artworkTarget.isBlank() && !importMenu && !albumEditor && !folderDialog && moveToFolder.isEmpty() && !organize && !appearance && deletion == null && !confirmBatch && resumeEntry == null && addToList.isEmpty()) { navigateBack() }
    LaunchedEffect(collection) { gridState.scrollToItem(0) }
    LaunchedEffect(collection,collections.folders) {
        if(collection.startsWith("folder:") && collections.folders.none { it.id == collection }) navigateCollection(LIBRARY_FOLDER_ROOT)
    }
    fun launchPlayer(uri: String? = null, position: Long? = null, playlist: String = collection) {
        transport.handOffToPlayer()
        context.startActivity(Intent(context,PlayerActivity::class.java).apply {
            if(uri != null) putExtra("uri",uri)
            putExtra("collection",playlist)
            putExtra("librarySort",sortName); putExtra("dubbedOnly",dubbedOnly)
            if(position != null) putExtra("startPosition",position)
        })
    }
    fun openPlayer(uri: String? = null) {
        val entry=entries.firstOrNull { it.uri == uri }
        if(entry != null && entry.video && uri != transport.uri && LibraryExtras.position(entry.uri,entry.durationMs) > 0)
            resumeEntry=entry
        else launchPlayer(uri)
    }
    fun toggleSelection(uri: String) {
        controlsExpanded=true
        selecting=true
        selected=ArrayList(if(uri in selected) selected-uri else selected+uri)
    }
    LaunchedEffect(batch?.deleted) {
        val removed=batch?.deleted.orEmpty()
        if(removed.isNotEmpty()) { entries=entries.filterNot { it.uri in removed }; selected=ArrayList(selected.filterNot { it in removed }) }
    }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if(uris.isNotEmpty()) scope.launch {
            importing=true; importedCount=0; importNotice=""; error=""
            try {
                val failed=LibraryRepository.addFiles(context,uris,LibrarySection.valueOf(pickerSection))
                error=if(failed > 0) "${failed}件を追加できませんでした。端末に保存した動画・音楽を選んでください。" else ""
                refresh++
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { error="曲・動画を取り込めませんでした。保存先のアクセス権を確認してください。" }
            finally { importing=false }
        }
    }
    val folderPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if(uri != null) scope.launch {
            importing=true; importedCount=0; importNotice=""; error=""
            try {
                val result=LibraryRepository.addAudioFolder(context,uri) { importedCount=it }
                importNotice=if(result.found == 0 && result.failures == 0) "音楽ファイルが見つかりませんでした。"
                    else "${result.added}曲を追加 · ${result.duplicate}曲は取り込み済み"+
                        if(result.failures > 0) " · ${result.failures}件を読み込めませんでした" else ""
                refresh++
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { error="フォルダーを取り込めませんでした。保存先のアクセス権を確認して再試行してください。" }
            finally { importing=false }
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
    val savedAudioChanges by SavedAudioMetadata.changes.collectAsStateWithLifecycle()
    LaunchedEffect(refresh,savedAudioChanges) {
        loading=true
        try { entries=LibraryRepository.load(context) }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { error="一覧を読み込めませんでした。「更新」で再試行してください。"; AppLog.error("ライブラリー",e) }
        finally { loading=false }
    }
    val sectionItems=remember(entries,section) { sectionEntries(entries,section) }
    val albums=remember(entries,collections) { libraryAlbums(entries,collections) }
    val shownAlbums=remember(albums,search) {
        albums.filter { it.name.contains(search,true) || it.artist.contains(search,true) || it.tracks.any { track -> track.title.contains(search,true) || track.artist.contains(search,true) } }
            .sortedBy { libraryTitleKey(it.name) }
    }
    val currentAlbum=albums.firstOrNull { it.matches(collection) }
    LaunchedEffect(collection,currentAlbum?.id) {
        if(currentAlbum != null && collection != currentAlbum.id) collection=currentAlbum.id
    }
    val filteredEntries=remember(sectionItems,filter,search) { libraryEntries(sectionItems,emptyList(),filter,search) }
    val visible=remember(filteredEntries,collections,collection,sort,dubbedOnly) {
        organizedLibrary(filteredEntries,collections,collection,sort,dubbedOnly)
    }
    val folderRows=remember(filteredEntries,collections,dubbedOnly,sectionItems,entries,sort) {
        val known=entries.map { it.uri }.toSet(); val own=sectionItems.map { it.uri }.toSet()
        libraryFolderRows(filteredEntries,collections,dubbedOnly).filter { row ->
            val members=collections.folders.first { it.id == row.id }.uris
            members.none { it in known } || members.any { it in own }
        }.let { if(sort == LibrarySort.TITLE) it.sortedBy { row -> libraryTitleKey(row.name) } else it }
    }
    val indexRows=remember(albumRoot,folderRoot,shownAlbums,folderRows,visible) {
        when {
            albumRoot -> shownAlbums.map { LibraryIndexRow("album-row:${it.id}",it.name) }
            folderRoot -> (if(folderRows.isEmpty()) listOf(LibraryIndexRow("no-folders","")) else folderRows.map { LibraryIndexRow("folder-row:${it.id}",it.name) })+
                LibraryIndexRow("unfiled-heading","")+visible.map { LibraryIndexRow(it.uri,it.title) }
            else -> visible.map { LibraryIndexRow(it.uri,it.title) }
        }
    }
    val indexTargets=remember(indexRows) { libraryIndexTargets(indexRows) }
    LaunchedEffect(indexLabel,indexRows) { indexLabel?.let { label -> libraryIndexTargets(indexRows)[label]?.let { gridState.scrollToItem(it) } } }
    val playingEntry=entries.firstOrNull { it.uri == playback.uri }
    val playingArtwork by rememberLibraryArtwork(playingEntry)
    val playingColor=remember(playingArtwork) { playingArtwork?.let { libraryThumbnailColor(it) } }
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
            LibraryHeader(headerAccent,loading,::navigateBack,onRefresh={error=""; refresh++})
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                LibrarySection.entries.forEach { tab -> FilterChip(selected=section == tab,modifier=Modifier.weight(1f),
                    onClick={sectionName=tab.name; filter="すべて"; search=""; dubbedOnly=false; navigateCollection("")},label={Text(tab.label)}) }
            }
            if(section == LibrarySection.MUSIC) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                FilterChip(selected=!albumBrowsing,onClick={navigateCollection("")},label={Text("曲")})
                FilterChip(selected=albumBrowsing,onClick={navigateCollection(MUSIC_ALBUM_ROOT); controlsExpanded=false},label={Text("アルバム")})
                TextButton(onClick={editingAlbum=""; albumEditor=true},enabled=!importing) { Text("＋ アルバムを追加") }
            }
            val controlsSummary=listOfNotNull(
                filter.takeUnless { it == "すべて" },
                "検索中".takeIf { search.isNotBlank() },
                if(albumBrowsing) currentAlbum?.name ?: "アルバム" else if(folderRoot) "フォルダー一覧" else if(collection == "favorites") "お気に入り" else (collections.playlists+collections.folders).firstOrNull { it.id == collection }?.name ?: "未分類".takeIf { collection == "unfiled" },
                "オートダビング版".takeIf { dubbedOnly },
                "${selected.size}件選択".takeIf { selecting }
            ).joinToString(" · ")
            LibraryControlsPanel(controlsExpanded,controlsSummary,onExpandedChange={
                controlsExpanded=it
                if(!it) { focusManager.clearFocus(); keyboard?.hide() }
            }) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text(if(section == LibrarySection.MUSIC) "音楽ライブラリー" else "動画ライブラリー",fontSize=13.sp)
                Box {
                    OutlinedButton(onClick={
                        if(section == LibrarySection.MUSIC) importMenu=true
                        else { pickerSection=sectionName; picker.launch(arrayOf(section.mime)) }
                    },enabled=!importing) {
                        Text(if(importing) "取り込み中…" else if(section == LibrarySection.MUSIC) "曲を取り込む" else "動画を取り込む")
                    }
                    DropdownMenu(importMenu,{importMenu=false}) {
                        DropdownMenuItem(text={Text("フォルダーをまとめて取り込む")},onClick={importMenu=false; folderPicker.launch(null)})
                        DropdownMenuItem(text={Text("曲を選んで取り込む")},onClick={importMenu=false; pickerSection=LibrarySection.MUSIC.name; picker.launch(arrayOf("audio/*"))})
                    }
                }
            }
            LibraryBrowseButtons(searchExpanded,onFolder={navigateCollection(LIBRARY_FOLDER_ROOT)},onSearch={
                if(searchExpanded) { search=""; focusManager.clearFocus(); keyboard?.hide() }
                searchExpanded=!searchExpanded
            })
            if(searchExpanded) {
                OutlinedTextField(search,{search=it},Modifier.fillMaxWidth().focusRequester(searchFocus),singleLine=true,
                    label={Text("タイトルで検索")},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
                    keyboardActions=KeyboardActions(onSearch={focusManager.clearFocus(); keyboard?.hide()}))
                LaunchedEffect(Unit) { if(!appearance) { searchFocus.requestFocus(); keyboard?.show() } }
            }
            if(section == LibrarySection.VIDEO) FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("すべて","動画","ショート").forEach { option -> FilterChip(selected=filter == option,onClick={filter=option},label={Text(option)}) }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={organize=true},modifier=Modifier.weight(1f)) {
                    val name=if(folderRoot) "フォルダー一覧" else if(collection == "favorites") "♥ お気に入り" else (collections.playlists+collections.folders).firstOrNull { it.id == collection }?.name ?: "未分類".takeIf { collection == "unfiled" } ?: "整理・並び替え"
                    Text(name+(if(dubbedOnly) " · オートダビング版" else ""),maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                TextButton(onClick={selecting=!selecting; selected=arrayListOf()}) { Text(if(selecting) "選択を終了" else "選択") }
                LibraryAppearanceButton(headerAccent) { focusManager.clearFocus(); keyboard?.hide(); appearance=true }
                LibraryLayoutButton(layout,headerAccent,onOpen={focusManager.clearFocus(); keyboard?.hide()},
                    onSelect={Store.preferences(preferences.copy(libraryLayout=it.id))})
                if(playback.uri.isNotBlank() && playingEntry?.video == (section == LibrarySection.VIDEO)) PlaybackBetaMenu(transport.controller)
            }
            if(selecting) FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.Center) {
                Text("${selected.size}件選択",Modifier.padding(vertical=12.dp))
                TextButton(onClick={selected=ArrayList(visible.map { it.uri })}) { Text("表示中を全選択") }
                TextButton(enabled=selected.isNotEmpty(),onClick={addToList=ArrayList(selected)}) { Text("リストへ") }
                TextButton(enabled=selected.isNotEmpty(),onClick={moveToFolder=ArrayList(selected)}) { Text("フォルダーへ") }
                TextButton(enabled=selected.isNotEmpty(),onClick={confirmBatch=true}) { Text("削除") }
            }
            }
            if(folderBrowsing) LibraryFolderPath(
                name=if(folderRoot) null else if(collection == "unfiled") "未分類" else collections.folders.firstOrNull { it.id == collection }?.name,
                onRoot={navigateCollection(LIBRARY_FOLDER_ROOT)},onAll={navigateCollection("")},onManage={folderDialog=true})
            if(albumBrowsing && !albumRoot) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={navigateCollection(MUSIC_ALBUM_ROOT)}) { Text("アルバム ›") }
                Text(currentAlbum?.name ?: "アルバムが見つかりません",Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                currentAlbum?.let { album -> TextButton(onClick={editArtwork(album.tracks.firstOrNull(),album)}) { Text("画像") } }
                if(currentAlbum?.manual == true) TextButton(onClick={editingAlbum=collection; albumEditor=true}) { Text("編集") }
            }
            if(preferences.libraryPlayerVisible && playback.uri.isNotBlank() && playingEntry?.video == (section == LibrarySection.VIDEO)) {
                val panel=if(transport.panel.uri == playback.uri) transport.panel.copy(title=playback.title,advancing=transport.panel.advancing && !appearance)
                    else LibraryPlayerState(uri=playback.uri,title=playback.title)
                LibraryPlayerPanel(panel,playingArtwork,playingColor,preferences.libraryPlayerScale,
                    onOpen={openPlayer()},onToggle={transport.toggle(panel.uri)},
                    onPrevious={transport.previous(panel.uri)},onNext={transport.next(panel.uri)},onSeek={transport.seek(panel.uri,it)},
                    waveform=preferences.libraryPlayerWave,effect=PlaybackEffect.fromId(preferences.libraryPlayerEffect))
            }
            if(transport.error.isNotBlank()) Text(transport.error,fontSize=13.sp)
            if(error.isNotBlank()) Text(error,color=if(hasWallpaper) Color.White else MaterialTheme.colorScheme.error,fontSize=13.sp)
            if(importing) Text("フォルダー・曲を読み込み中… ${importedCount}曲",fontSize=13.sp)
            if(importNotice.isNotBlank()) Text(importNotice,fontSize=13.sp)
            if(loading || importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(visible.isEmpty() && !loading && !folderRoot && !albumRoot) Text(if(sectionItems.isEmpty()) "保存した${section.label}がここに表示されます。" else "該当するファイルはありません。",Modifier.padding(vertical=24.dp))
            }
            }
            }
            val controlsPull=rememberLibraryControlsPull(controlsExpanded,atTop={!gridState.canScrollBackward},onExpand={controlsExpanded=true},
                onCollapse={controlsExpanded=false; focusManager.clearFocus(); keyboard?.hide()})
            Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(columns=GridCells.Fixed(layout.columns),state=gridState,modifier=Modifier.fillMaxSize().padding(end=24.dp).nestedScroll(controlsPull),
                horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(vertical=8.dp)) {
                if(albumRoot) {
                    items(shownAlbums,key={"album-row:${it.id}"},span={GridItemSpan(maxLineSpan)}) { album ->
                        LibraryAlbumTile(album,dark,backdrop,layout,onPlay={
                            album.tracks.firstOrNull()?.let { launchPlayer(it.uri,playlist=album.id) }
                        },onOpen={navigateCollection(album.id)})
                    }
                    if(shownAlbums.isEmpty()) item(key="albums-empty",span={GridItemSpan(maxLineSpan)}) {
                        LibraryFolderCaption("アルバム情報のある曲を取り込むか、「アルバムを追加」で曲をまとめられます。",dark,backdrop)
                    }
                }
                if(folderRoot) {
                    items(folderRows,key={"folder-row:${it.id}"},span={GridItemSpan(maxLineSpan)}) { folder ->
                        LibraryFolderTile(folder,headerAccent,dark,backdrop,onOpen={navigateCollection(folder.id)})
                    }
                    if(folderRows.isEmpty()) item(key="no-folders",span={GridItemSpan(maxLineSpan)}) {
                        LibraryFolderCaption("フォルダー管理からフォルダーを作成できます。",dark,backdrop)
                    }
                    item(key="unfiled-heading",span={GridItemSpan(maxLineSpan)}) {
                        LibraryFolderCaption("未分類 · ${visible.size}件",dark,backdrop)
                    }
                    if(visible.isEmpty() && !loading) item(key="unfiled-empty",span={GridItemSpan(maxLineSpan)}) {
                        LibraryFolderCaption("未分類のファイルはありません。",dark,backdrop)
                    }
                }
                items(visible,key={it.uri}) { item ->
                    LibraryCard(item,dark,backdrop,transport,layout,onDelete={if(!importing) deletion=item},
                        onArtwork={editArtwork(item)},
                        favorite=item.uri in collections.favorites,selected=item.uri in selected,
                        selectionMode=selecting,onLongClick={toggleSelection(item.uri)},
                        onFavorite={runCatching { LibraryExtras.favorite(item.uri) }.onFailure { error="お気に入りを保存できませんでした。" }},
                        onPlaylist={addToList=arrayListOf(item.uri)},onFolder={moveToFolder=arrayListOf(item.uri)},onNext={
                            runCatching { enqueueNext(transport.controller ?: error("先に再生を開始してください。"),item) }
                                .onFailure { error=it.message ?: "再生順を変更できませんでした。" }
                        },onRemoveFromList=if(collections.playlists.any { it.id == collection }) ({
                            runCatching { LibraryExtras.removeFrom(collection,setOf(item.uri)) }.onFailure { error="リストを更新できませんでした。" }
                            Unit
                        }) else null) {
                        if(selecting) toggleSelection(item.uri) else openPlayer(item.uri)
                    }
                }
            }
            LibraryIndexRail(indexTargets,onStart={sortName=LibrarySort.TITLE.name; controlsExpanded=false; focusManager.clearFocus(); keyboard?.hide()},onJump={indexLabel=it})
            }
        }
    }
    }
    if(appearance) LibraryAppearanceDialog(wallpaper,preferences,playingEntry ?: visible.firstOrNull(),headerAccent,colors,
        onChange=Store::preferences,onDismiss={appearance=false},previewEntries=visible.take(6))
    if(artworkTarget.isNotBlank()) key(artworkTarget) {
        LibraryArtworkEditor(artworkTarget,artworkTitle,entries.firstOrNull { it.uri == artworkTrack },artworkAlbum,onDismiss={artworkTarget=""})
    }
    UpdatePrompt(allowed=artworkTarget.isBlank() && !importMenu && !albumEditor && !appearance && deletion == null && !importing && error.isBlank() && !organize && addToList.isEmpty() && !confirmBatch && resumeEntry == null && batch == null && !folderDialog && moveToFolder.isEmpty())
    if(albumEditor) LibraryAlbumEditor(entries,collections.albums.firstOrNull { it.id == editingAlbum },
        onSaved={id -> albumEditor=false; navigateCollection(id)},onDismiss={albumEditor=false})
    if(folderDialog) LibraryFolderDialog(collection,onSelect=::navigateCollection,onDismiss={folderDialog=false})
    if(moveToFolder.isNotEmpty()) LibraryFolderDialog(collection,onSelect={},onDismiss={moveToFolder=arrayListOf()},
        moving=moveToFolder,onMoved={moveToFolder=arrayListOf(); selected=arrayListOf(); selecting=false})
    if(organize) LibraryOrganizeDialog(collection,sort,dubbedOnly,onCollection=::navigateCollection,onSort={sortName=it.name},
        onDubbed={dubbedOnly=it},onDismiss={organize=false},allowDubbed=section == LibrarySection.VIDEO)
    if(addToList.isNotEmpty()) AddToPlaylistDialog(addToList,onDismiss={addToList=arrayListOf()},onAdded={addToList=arrayListOf(); selected=arrayListOf(); selecting=false})
    resumeEntry?.let { entry -> ResumePlaybackDialog(entry,LibraryExtras.position(entry.uri,entry.durationMs),
        onStart={position -> resumeEntry=null; launchPlayer(entry.uri,position)},onDismiss={resumeEntry=null}) }
    if(confirmBatch) AlertDialog(onDismissRequest={confirmBatch=false},title={Text("${selected.size}件のファイルを削除しますか？")},
        text={Text("保存元の動画・音楽も削除します。この操作は元に戻せません。")},
        confirmButton={TextButton(onClick={
            LibraryBatchDeletion.start(context,entries.filter { it.uri in selected }); confirmBatch=false
        }) { Text("まとめて削除") }},dismissButton={TextButton(onClick={confirmBatch=false}) { Text("キャンセル") }})
    BatchDeletionProgress()
    deletion?.let { entry -> key(entry.uri) {
        LibraryDeleteDialog(entry,onDismiss={deletion=null},onRemoved={
            entries=entries.filterNot { it.uri == entry.uri }; refresh++
        })
    } }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryCard(entry: LibraryEntry, dark: Boolean, backdrop: LibraryBackdrop, playback: LibraryPlayback,
    layout: LibraryLayout = LibraryLayout.STANDARD, onDelete: ()->Unit, favorite: Boolean = false, selected: Boolean = false,
    selectionMode: Boolean = false, onLongClick: ()->Unit = {}, onFavorite: ()->Unit = {}, onPlaylist: ()->Unit = {},
    onNext: ()->Unit = {}, onFolder: ()->Unit = {}, onArtwork: ()->Unit = {}, onRemoveFromList: (()->Unit)? = null, onClick: ()->Unit) {
    val context=LocalContext.current
    val bitmap by rememberLibraryArtwork(entry)
    val source=remember(bitmap) { bitmap?.let { libraryThumbnailColor(it) } }
    val palette=remember(source,dark) { libraryPalette(source,dark) }
    var menu by remember { mutableStateOf(false) }
    Surface(shape=RoundedCornerShape(16.dp),color=Color.Transparent,contentColor=palette.content,
        border=if(layout == LibraryLayout.DETAILS && !selected) null else BorderStroke(if(selected) 3.dp else 1.dp,palette.content.copy(alpha=if(selected) 1f else .25f)),
        modifier=Modifier.fillMaxWidth().libraryCardGlass(palette,backdrop)
            .combinedClickable(onClick=onClick,onLongClick=onLongClick)) {
        LibraryCardBody(entry,bitmap,palette,layout,
            play={if(selectionMode) Checkbox(checked=selected,onCheckedChange={onClick()}) else LibraryPlayButton(entry.uri,playback,palette,onClick,outlined=layout == LibraryLayout.DETAILS)},menu={
                Box {
                    IconButton(onClick={menu=true},modifier=Modifier.semantics { contentDescription="${entry.title}のメニュー" }) {
                        Text(if(favorite) "♥⋮" else "⋮",fontSize=22.sp,color=palette.content)
                    }
                    DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                        if(!entry.video) DropdownMenuItem(text={Text("アートワークを変更")},onClick={menu=false; onArtwork()})
                        DropdownMenuItem(text={Text(if(favorite) "お気に入りを解除" else "お気に入りに追加")},onClick={menu=false; onFavorite()})
                        DropdownMenuItem(text={Text("フォルダーへ移動")},onClick={menu=false; onFolder()})
                        DropdownMenuItem(text={Text("プレイリストに追加")},onClick={menu=false; onPlaylist()})
                        DropdownMenuItem(text={Text("次に再生")},onClick={menu=false; onNext()})
                        DropdownMenuItem(text={Text("選択")},onClick={menu=false; onLongClick()})
                        if(onRemoveFromList != null) DropdownMenuItem(text={Text("このリストから外す")},onClick={menu=false; onRemoveFromList()})
                        DropdownMenuItem(text={Text("削除")},onClick={menu=false; onDelete()})
                    }
                }
            },footer={if(!selectionMode && playback.uri == entry.uri) LibrarySeekBar(entry.uri,playback,palette,source,backdrop,compact=layout != LibraryLayout.STANDARD)})
    }
}
