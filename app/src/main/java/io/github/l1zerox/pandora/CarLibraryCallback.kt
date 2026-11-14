package io.github.l1zerox.pandora

import android.content.Context
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.MediaStore
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.*
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@androidx.annotation.OptIn(UnstableApi::class)
internal class CarLibraryCallback(private val context: Context,
    private val loadEntries: suspend ()->List<LibraryEntry> = { LibraryRepository.load(context) },
    private val collections: ()->LibraryCollections = { LibraryExtras.state.value }): MediaLibrarySession.Callback {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val mutex=Mutex()
    private var cached: CarMusicCatalog?=null
    private var loadedAt=0L
    private var revision=0L
    private val visited=mutableSetOf(CAR_ROOT,CAR_SONGS,CAR_ALBUMS,CAR_FAVORITES,CAR_PLAYLISTS)
    private var session: MediaLibrarySession?=null
    private val observer=object: ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { invalidate() } }

    fun attach(value: MediaLibrarySession) {
        session=value
        runCatching { context.contentResolver.registerContentObserver(MediaStore.Downloads.EXTERNAL_CONTENT_URI,true,observer) }
        scope.launch {
            combine(LibraryExtras.state,LibraryArtwork.state,LibraryRepository.revision) { data,covers,imports -> Triple(data,covers,imports) }
                .distinctUntilChanged().collect { invalidate() }
        }
    }
    private fun invalidate() {
        revision++; cached=null
        visited.forEach { session?.notifyChildrenChanged(it,Int.MAX_VALUE,null) }
    }
    fun close() { context.contentResolver.unregisterContentObserver(observer); scope.cancel(); session=null }

    private fun own(controller: MediaSession.ControllerInfo)=controller.uid == Process.myUid()
    private fun allowed(controller: MediaSession.ControllerInfo): Boolean {
        val local=own(controller)
        val keys=if(!local && !controller.isTrusted && controller.isPackageNameVerified) carInstalledCertificateHashes(context,controller.packageName) else emptySet()
        return carClientAllowed(local,controller.isTrusted,controller.isPackageNameVerified,controller.packageName,keys)
    }

    override fun onConnectAsync(session: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.ConnectionResult> {
        val accepted=allowed(controller)
        if(!own(controller)) AppLog.info("Android Auto","接続 ${controller.packageName}: 許可=$accepted / trusted=${controller.isTrusted} / verified=${controller.isPackageNameVerified}")
        return Futures.immediateFuture(if(!accepted) MediaSession.ConnectionResult.reject() else
            MediaSession.ConnectionResult.AcceptedResultBuilder()
                .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS)
                .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS).build())
    }

    private suspend fun catalog(): CarMusicCatalog = mutex.withLock {
        cached?.takeIf { SystemClock.elapsedRealtime()-loadedAt < 10_000 }?.let { return@withLock it }
        val stamp=revision
        val entries=loadEntries()
        val data=collections()
        withContext(Dispatchers.Default) { CarMusicCatalog(entries,data) }.also {
            if(stamp == revision) { cached=it; loadedAt=SystemClock.elapsedRealtime() }
        }
    }
    private fun <T> later(block: suspend ()->T): ListenableFuture<T> {
        val future=SettableFuture.create<T>()
        val job=scope.launch {
            try { future.set(block()) }
            catch(e: CancellationException) { future.cancel(false); throw e }
            catch(e: Exception) { AppLog.error("車載ライブラリー",e); future.setException(e) }
        }
        future.addListener({ if(future.isCancelled) job.cancel() },MoreExecutors.directExecutor())
        return future
    }
    private fun rootItem()=MediaItem.Builder().setMediaId(CAR_ROOT).setMediaMetadata(MediaMetadata.Builder()
        .setTitle("Youtube Pandora!").setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        .setExtras(Bundle().apply {
            putBoolean("android.media.browse.SEARCH_SUPPORTED",true)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        }).build()).build()

    override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
        // Legacy Android Auto may block the main thread for the root. Never wait for disk here.
        val result=LibraryParams.Builder().setExtras(Bundle(params?.extras ?: Bundle.EMPTY).apply {
            putAll(rootItem().mediaMetadata.extras ?: Bundle.EMPTY)
        }).build()
        AppLog.info("Android Auto","ライブラリー入口: ${browser.packageName}")
        return Futures.immediateFuture(LibraryResult.ofItem(rootItem(),result))
    }
    private suspend fun browseItem(node: CarMusicNode, catalog: CarMusicCatalog, caller: String): MediaItem = withContext(Dispatchers.IO) {
        val metadata=MediaMetadata.Builder().setTitle(node.title).setSubtitle(node.subtitle)
            .setIsBrowsable(node.entry == null).setIsPlayable(node.entry != null)
            .setMediaType(if(node.entry != null) MediaMetadata.MEDIA_TYPE_MUSIC else if(node.album != null) MediaMetadata.MEDIA_TYPE_ALBUM else MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .setExtras(Bundle().apply {
                putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM,if(node.entry == null) MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM else MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
                putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
                putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
            })
        node.entry?.let { metadata.setArtist(it.artist).setAlbumTitle(it.album).setAlbumArtist(it.albumArtist); if(it.durationMs > 0) metadata.setDurationMs(it.durationMs) }
        catalog.coverEntry(node)?.let { metadata.setArtworkUri(carArtworkUri(context,it,if(node.entry == null) node.album else null,caller)) }
        MediaItem.Builder().setMediaId(node.id).setMediaMetadata(metadata.build()).build()
    }
    override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> = later {
        if(mediaId == CAR_ROOT) LibraryResult.ofItem(rootItem(),null) else {
            val catalog=catalog(); val node=catalog.item(mediaId)
            if(node == null) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE) else LibraryResult.ofItem(browseItem(node,catalog,browser.packageName),null)
        }
    }
    override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = later {
        val catalog=catalog(); val rows=catalog.children(parentId,page,pageSize)
        if(rows == null) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE) else {
            visited.add(parentId)
            val section=when { parentId == CAR_ROOT -> "カテゴリー"; parentId == CAR_ALBUMS -> "アルバム"; parentId.startsWith("car:album:") -> "アルバム内の曲"; else -> "一覧" }
            AppLog.info("Android Auto","$section: ${rows.size}件")
            LibraryResult.ofItemList(rows.map { browseItem(it,catalog,browser.packageName) },params)
        }
    }
    override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> = later {
        val count=catalog().search(query).size
        session.notifySearchResultChanged(browser,query,count,params)
        LibraryResult.ofVoid(params)
    }
    override fun onGetSearchResult(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = later {
        val catalog=catalog()
        LibraryResult.ofItemList(carPage(catalog.search(query),page,pageSize).map { browseItem(it,catalog,browser.packageName) },params)
    }
    private suspend fun playbackItems(entries: List<LibraryEntry>, caller: String): List<MediaItem> = withContext(Dispatchers.IO) {
        entries.map { entry ->
            val item=entry.mediaItem()
            item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setIsBrowsable(false).setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).setArtworkUri(carArtworkUri(context,entry,null,caller)).build()).build()
        }
    }
    override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> {
        if(own(controller)) return Futures.immediateFuture(mediaItems)
        return later {
            val catalog=catalog()
            val entries=mediaItems.map { catalog.item(it.mediaId)?.entry ?: error("音楽が見つかりません。") }
            playbackItems(entries,controller.packageName)
        }
    }
    override fun onSetMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        if(own(controller) && mediaItems.all { it.localConfiguration != null && it.requestMetadata.searchQuery.isNullOrBlank() })
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(mediaItems,startIndex,startPositionMs))
        return later {
            require(mediaItems.isNotEmpty())
            val catalog=catalog()
            val index=if(startIndex == C.INDEX_UNSET) 0 else startIndex
            require(index in mediaItems.indices)
            val request=mediaItems[index]
            val query=request.requestMetadata.searchQuery
            val queue=if(!query.isNullOrBlank()) {
                val matches=catalog.search(query).mapNotNull { it.entry }; require(matches.isNotEmpty()) { "曲が見つかりません。" }; CarMusicQueue(matches,0)
            } else if(mediaItems.size == 1) catalog.queue(request.mediaId) ?: error("音楽が見つかりません。")
            else CarMusicQueue(mediaItems.map { catalog.item(it.mediaId)?.entry ?: error("音楽が見つかりません。") },index)
            MediaSession.MediaItemsWithStartPosition(playbackItems(queue.entries,controller.packageName),queue.index,
                carStartPosition(startPositionMs,queue.entries[queue.index].durationMs))
        }
    }
    override fun onPlaybackResumption(session: MediaSession, controller: MediaSession.ControllerInfo, isForPlayback: Boolean): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = later {
        val catalog=catalog()
        val last=context.getSharedPreferences("car_playback",0).getString("last_uri",null)
        val queue=catalog.queue(last.orEmpty()) ?: catalog.songs.takeIf { it.isNotEmpty() }?.let { CarMusicQueue(it,0) } ?: error("スマホで音楽をライブラリーへ追加してください。")
        val entry=queue.entries[queue.index]
        val items=if(isForPlayback) queue.entries else listOf(entry)
        MediaSession.MediaItemsWithStartPosition(playbackItems(items,controller.packageName),if(isForPlayback) queue.index else 0,LibraryExtras.position(entry.uri,entry.durationMs))
    }
}
