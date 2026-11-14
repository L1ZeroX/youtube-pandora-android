package io.github.l1zerox.pandora

import android.app.SearchManager
import android.content.ComponentName
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Standard voice-play intent; uses the same session and music-only resolver as the car browser. */
class VoiceSearchActivity: ComponentActivity() {
    private var connection: ListenableFuture<MediaController>?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(intent.action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) { finish(); return }
        val query=intent.getStringExtra(SearchManager.QUERY).orEmpty()
            .ifBlank { intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE).orEmpty() }.trim().take(200)
        lifecycleScope.launch {
            try {
                withTimeout(20_000) {
                    val future=MediaController.Builder(this@VoiceSearchActivity,SessionToken(this@VoiceSearchActivity,
                        ComponentName(this@VoiceSearchActivity,PlaybackService::class.java))).buildAsync()
                    connection=future
                    val controller=suspendCancellableCoroutine { continuation ->
                        future.addListener({
                            if(continuation.isActive) runCatching { future.get() }
                                .onSuccess { continuation.resume(it) }.onFailure { continuation.resumeWithException(it) }
                        },ContextCompat.getMainExecutor(this@VoiceSearchActivity))
                    }
                    if(query.isNotBlank() || controller.currentMediaItem == null || controller.currentMediaItem?.mediaMetadata?.extras?.getBoolean("video") == true) {
                        val request=MediaItem.Builder().setMediaId(if(query.isBlank()) CAR_SONGS else "car:search")
                            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery(query.takeIf { it.isNotBlank() }).build()).build()
                        controller.setMediaItem(request)
                    }
                    controller.prepare(); controller.play()
                    if(!controller.isPlaying) suspendCancellableCoroutine { continuation ->
                        val listener=object: Player.Listener {
                            override fun onEvents(player: Player, events: Player.Events) {
                                if(!continuation.isActive) return
                                if(player.playerError != null) { player.removeListener(this); continuation.resumeWithException(player.playerError!!) }
                                else if(player.isPlaying) { player.removeListener(this); continuation.resume(Unit) }
                            }
                        }
                        controller.addListener(listener)
                        continuation.invokeOnCancellation { controller.removeListener(listener) }
                    }
                }
            } catch(e: TimeoutCancellationException) {
                Toast.makeText(this@VoiceSearchActivity,"音楽を開始できませんでした。スマホのライブラリーを確認してください。",Toast.LENGTH_LONG).show()
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { Toast.makeText(this@VoiceSearchActivity,"音楽を開始できませんでした。スマホのライブラリーを確認してください。",Toast.LENGTH_LONG).show() }
            finally { finish() }
        }
    }
    override fun onDestroy() { connection?.let { MediaController.releaseFuture(it) }; connection=null; super.onDestroy() }
}
