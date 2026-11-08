package io.github.l1zerox.pandora

import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

internal data class SleepTimerStatus(val remainingSeconds: Long = 0, val endOfItem: Boolean = false) {
    val active get()=remainingSeconds > 0 || endOfItem
    val label get()=if(endOfItem) "この曲・動画の終了まで" else if(active) "残り ${durationText(remainingSeconds.toDouble())}" else "オフ"
}
internal object PlaybackBetaRuntime {
    val timer=MutableStateFlow(SleepTimerStatus())
    var setTimer: ((Int)->Unit)? = null
}

@androidx.annotation.OptIn(UnstableApi::class)
internal class PlaybackBetaTools(private val player: ExoPlayer) : Player.Listener {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var plan=SleepTimerPlan()
    init {
        player.addListener(this)
        PlaybackBetaRuntime.setTimer=::setTimer
        scope.launch { while(isActive) {
            save()
            if(plan.deadline > 0) {
                val now=SystemClock.elapsedRealtime()
                if(plan.expired(now)) { player.pause(); setTimer(0) }
                else PlaybackBetaRuntime.timer.value=plan.status(now)
            }
            delay(1000)
        } }
    }
    private fun save() { LibraryExtras.rememberPosition(player.currentMediaItem?.mediaId.orEmpty(),player.currentPosition,player.duration) }
    private fun setTimer(minutes: Int) {
        val now=SystemClock.elapsedRealtime()
        plan=SleepTimerPlan.start(minutes,now,player.currentMediaItem?.mediaId.orEmpty())
        player.pauseAtEndOfMediaItems=plan.endingUri.isNotBlank()
        PlaybackBetaRuntime.timer.value=plan.status(now)
    }
    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        oldPosition.mediaItem?.let { old ->
            LibraryExtras.rememberPosition(old.mediaId,oldPosition.positionMs,old.mediaMetadata.extras?.getLong("durationMs") ?: 0)
        }
        if(plan.leavesItem(newPosition.mediaItem?.mediaId.orEmpty())) setTimer(0)
    }
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        save()
        if(!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && plan.endingUri.isNotBlank()) setTimer(0)
    }
    override fun onPlaybackStateChanged(playbackState: Int) {
        save()
        if(playbackState == Player.STATE_ENDED && PlaybackBetaRuntime.timer.value.endOfItem) setTimer(0)
    }
    fun close() { save(); scope.cancel(); player.removeListener(this); setTimer(0); PlaybackBetaRuntime.setTimer=null }
}

internal fun enqueueNext(player: Player, entry: LibraryEntry) {
    check(player.currentMediaItem != null) { "先に動画・音楽を再生してください。" }
    check(player.currentMediaItem?.mediaMetadata?.extras?.getString("category") == entry.category) { "再生中と同じ種類の項目を選んでください。" }
    enqueueMediaNext(player,entry.mediaItem())
}

internal fun enqueueMediaNext(player: Player, item: androidx.media3.common.MediaItem) {
    check(player.currentMediaItem != null) { "先に動画・音楽を再生してください。" }
    check(player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) { "再生リストを変更できません。" }
    if(player.currentMediaItem?.mediaId == item.mediaId) return
    check(!player.shuffleModeEnabled || player.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)) { "シャッフルをオフにしてからお試しください。" }
    val existing=(0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == item.mediaId }
    val current=player.currentMediaItemIndex
    if(existing == null) player.addMediaItem(current+1,item)
    else player.moveMediaItem(existing,if(existing < current) current else current+1)
    // Explicit "next" follows the displayed order, even if shuffle was previously enabled.
    if(player.shuffleModeEnabled && player.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)) player.shuffleModeEnabled=false
}
