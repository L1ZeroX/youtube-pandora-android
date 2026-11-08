package io.github.l1zerox.pandora

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)
    private var future: ListenableFuture<MediaController>? = null
    private var playerView: PlayerView? = null
    private var loadJob: Job? = null
    private var artJob: Job? = null
    private var artworkUri=""
    private var artworkColor by mutableStateOf<Color?>(null)
    private var artwork by mutableStateOf<Bitmap?>(null)
    private var title by mutableStateOf("読み込み中…")
    private var video by mutableStateOf(false)
    private var inPip by mutableStateOf(false)
    private var landscape by mutableStateOf(false)
    private var appliedFullscreen: Boolean? = null
    internal val videoFullscreen get() = video && landscape && !inPip
    private var error by mutableStateOf("")
    private var connected by mutableStateOf(false)
    private var requestedUri: String? = null
    private var restorePosition=0L
    private var restoring=false
    private val supportsPip get() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    private val listener=object: Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { updatePlayer() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        landscape=resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        WindowCompat.getInsetsController(window,window.decorView).apply {
            isAppearanceLightStatusBars=false; isAppearanceLightNavigationBars=false
        }
        requestedUri=savedInstanceState?.getString("uri") ?: intent.getStringExtra("uri")
        restorePosition=savedInstanceState?.getLong("position") ?: 0
        restoring=savedInstanceState != null
        onBackPressedDispatcher.addCallback(this) { closePlayerScreen() }
        setContent {
            MaterialTheme(colorScheme=darkColorScheme()) {
                UpdatePrompt(allowed=!inPip && !videoFullscreen && error.isBlank())
                if(!video && !inPip) {
                    MusicPlayerScreen(controller,title,artwork,artworkColor,error,
                        onBack={closePlayerScreen()},onStop={controller?.stop(); controller?.clearMediaItems(); closePlayerScreen()})
                } else {
                Column(Modifier.fillMaxSize().background(Color.Black).then(if(inPip || videoFullscreen) Modifier else Modifier.safeDrawingPadding())) {
                    if(!inPip && !videoFullscreen) {
                        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            PlaybackColorGlass(artworkColor) {
                                TextButton(onClick={closePlayerScreen()},colors=ButtonDefaults.textButtonColors(contentColor=Color.White)) { Text("戻る") }
                            }
                            if(video && supportsPip) PlaybackColorGlass(artworkColor) {
                                TextButton(onClick={enterPip()},colors=ButtonDefaults.textButtonColors(contentColor=Color.White)) { Text("小窓で再生") }
                            }
                            Spacer(Modifier.weight(1f))
                            PlaybackBetaMenu(controller)
                            PlaybackColorGlass(artworkColor) {
                                TextButton(enabled=connected,onClick={controller?.stop(); controller?.clearMediaItems(); closePlayerScreen()},
                                    colors=ButtonDefaults.textButtonColors(contentColor=Color.White)) { Text("停止") }
                            }
                        }
                        PlaybackColorGlass(artworkColor,Modifier.fillMaxWidth().padding(horizontal=12.dp)) {
                            Text(title,Modifier.padding(horizontal=14.dp,vertical=10.dp),color=Color.White,maxLines=2,overflow=TextOverflow.Ellipsis)
                        }
                        if(error.isNotBlank()) Text(error,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error)
                    }
                    if(videoFullscreen && error.isNotBlank())
                        Text(error,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error)
                    AndroidView(factory={ context ->
                        PlayerView(context).also { view ->
                            view.player=controller
                            view.resizeMode=AspectRatioFrameLayout.RESIZE_MODE_FIT
                            view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            view.setShowNextButton(true); view.setShowPreviousButton(true)
                            stylePlaybackControls(view,artworkColor)
                            view.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> updatePip() }
                            playerView=view
                        }
                    },modifier=Modifier.weight(1f).fillMaxWidth(),onRelease={ view ->
                        view.player=null
                        if(playerView === view) playerView=null
                    },update={ view ->
                        view.player=controller
                        view.keepScreenOn=video && controller?.isPlaying == true
                        view.useController=!inPip
                        if(inPip) view.hideController()
                    })
                }
                }
            }
        }
        val connecting=MediaController.Builder(this,SessionToken(this,ComponentName(this,PlaybackService::class.java))).buildAsync()
        future=connecting
        connecting.addListener({
            if(isDestroyed || isFinishing) return@addListener
            runCatching {
                controller=connecting.get()
                controller!!.addListener(listener)
                playerView?.player=controller
                connected=true
                if(restoring && controller!!.mediaItemCount > 0) updatePlayer() else loadRequested()
            }.onFailure { error="再生を開始できませんでした。一度戻って再度お試しください。"; AppLog.error("プレーヤー接続",it) }
        },ContextCompat.getMainExecutor(this))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        requestedUri=intent.getStringExtra("uri"); restoring=false; restorePosition=0
        if(controller != null) loadRequested()
    }

    private fun loadRequested() {
        loadJob?.cancel()
        val player=controller ?: return
        val uri=requestedUri
        if(uri.isNullOrBlank()) {
            updatePlayer()
            if(player.mediaItemCount == 0) { title="再生するファイルを選んでください"; error="ライブラリーに戻って動画・音楽を選んでください。" }
            return
        }
        if(player.currentMediaItem?.mediaId == uri) {
            if(!restoring) {
                if(intent.hasExtra("startPosition")) player.seekTo(intent.getLongExtra("startPosition",0).coerceAtLeast(0))
                else if(player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                if(player.playbackState == Player.STATE_IDLE) player.prepare()
                if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) player.play()
            }
            updatePlayer(); return
        }
        loadJob=lifecycleScope.launch {
            try {
                error=""
                val entries=LibraryRepository.load(this@PlayerActivity)
                val selected=entries.firstOrNull { it.uri == uri } ?: error("保存先にファイルが見つかりません。")
                // Keep video, shorts and music separate; background music must never start hidden video.
                val collection=intent.getStringExtra("collection").orEmpty()
                val order=runCatching { LibrarySort.valueOf(intent.getStringExtra("librarySort").orEmpty()) }.getOrDefault(LibrarySort.NEWEST)
                val available=organizedLibrary(entries,LibraryExtras.state.value,collection,order,intent.getBooleanExtra("dubbedOnly",false))
                val playlist=available.filter { it.category == selected.category }.let { if(selected in it) it else listOf(selected)+it }
                val position=if(restoring) restorePosition else intent.getLongExtra("startPosition",0)
                player.setMediaItems(playlist.map { it.mediaItem() },playlist.indexOf(selected),position.coerceIn(0,selected.durationMs.coerceAtLeast(0)))
                player.prepare()
                if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) player.play()
                updatePlayer()
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { error="ファイルを開けません。移動・削除した場合はライブラリーから追加し直してください。"; AppLog.error("ライブラリー再生",e) }
        }
    }

    private fun updatePlayer() {
        val player=controller ?: return
        title=player.mediaMetadata.title?.toString() ?: "プレーヤー"
        video=player.currentMediaItem?.mediaMetadata?.extras?.getBoolean("video") == true
        updateWindowMode()
        error=player.playerError?.let { "再生できません。ファイルの場所・アクセス権、対応形式を確認してください。" }.orEmpty()
        playerView?.keepScreenOn=video && player.isPlaying
        val uri=player.currentMediaItem?.mediaId.orEmpty()
        if(uri != artworkUri) {
            artworkUri=uri; artJob?.cancel(); playerView?.defaultArtwork=null
            artworkColor=null; artwork=null
            playerView?.let { stylePlaybackControls(it,null) }
            val isVideo=video
            if(uri.isNotBlank()) artJob=lifecycleScope.launch {
                val art=LibraryRepository.artwork(this@PlayerActivity,LibraryEntry(uri,title,if(isVideo) "video/mp4" else "audio/mpeg"))
                val color=withContext(Dispatchers.Default) { art?.let { libraryThumbnailColor(it) } }
                if(artworkUri == uri) {
                    artworkColor=color
                    artwork=art
                    playerView?.let { stylePlaybackControls(it,artworkColor) }
                }
            }
        }
        updatePip()
    }

    private fun pipParams(auto: Boolean): PictureInPictureParams {
        val size=controller?.videoSize
        val ratio=pipAspect(size?.width ?: 0,size?.height ?: 0,size?.pixelWidthHeightRatio ?: 1f)
        // Rounding must not take an extremely tall video's ratio below Android's minimum of 1 / 2.39.
        val builder=PictureInPictureParams.Builder().setAspectRatio(Rational((ratio*10000).roundToInt().coerceIn(4185,23900),10000))
        val rect=Rect()
        if(playerView?.getGlobalVisibleRect(rect) == true && !rect.isEmpty) builder.setSourceRectHint(rect)
        if(Build.VERSION.SDK_INT >= 31) builder.setAutoEnterEnabled(auto).setSeamlessResizeEnabled(true)
        return builder.build()
    }
    private fun updatePip() {
        if(!supportsPip || isFinishing || isDestroyed) return
        val player=controller
        val auto=video && player?.playWhenReady == true && player.playbackState in listOf(Player.STATE_READY,Player.STATE_BUFFERING)
        runCatching { setPictureInPictureParams(pipParams(auto)) }
    }
    private fun enterPip() {
        if(!video || !supportsPip) return
        if(!runCatching { enterPictureInPictureMode(pipParams(false)) }.getOrDefault(false))
            Toast.makeText(this,"Androidの設定でピクチャーインピクチャーを許可してください。",Toast.LENGTH_LONG).show()
    }
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if(Build.VERSION.SDK_INT < 31 && video && controller?.isPlaying == true) enterPip()
    }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode,newConfig)
        inPip=isInPictureInPictureMode
        landscape=newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        updateWindowMode()
        if(inPip) BubbleRuntime.leave(this)
        else if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) BubbleRuntime.enter(this)
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        landscape=newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        updateWindowMode()
    }
    private fun updateWindowMode(force: Boolean = false) {
        if(inPip) { appliedFullscreen=null; return }
        val fullscreen=videoFullscreen
        if(!force && appliedFullscreen == fullscreen) return
        appliedFullscreen=fullscreen
        WindowCompat.getInsetsController(window,window.decorView).apply {
            systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if(fullscreen) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if(hasFocus) updateWindowMode(force=true)
    }
    private fun closePlayerScreen() {
        if(supportsPip) runCatching { setPictureInPictureParams(pipParams(false)) }
        finish()
    }
    override fun onStart() { super.onStart(); if(!isInPictureInPictureMode) BubbleRuntime.enter(this) }
    override fun onResume() { super.onResume(); BubbleRuntime.sync(this) }
    override fun onStop() {
        // A visible PiP Activity is paused, but not stopped. Closing PiP or hiding full-screen video pauses it.
        if(controller?.currentMediaItem?.mediaMetadata?.extras?.getBoolean("video") == true && !isChangingConfigurations) controller?.pause()
        super.onStop(); BubbleRuntime.leave(this)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("uri",controller?.currentMediaItem?.mediaId ?: requestedUri)
        outState.putLong("position",controller?.currentPosition ?: 0)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        loadJob?.cancel(); artJob?.cancel(); playerView?.player=null
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        controller=null; super.onDestroy()
    }
}

internal fun LibraryEntry.mediaItem(): MediaItem = MediaItem.Builder().setMediaId(uri).setUri(uri).setMimeType(mime)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title)
        .setExtras(Bundle().apply { putBoolean("video",video); putString("category",category); putLong("durationMs",durationMs) }).build()).build()
