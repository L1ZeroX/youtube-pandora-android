package io.github.l1zerox.pandora

import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackBetaRuntimeTest {
    @Test fun bookmarksAreSavedAndEndTimerPausesBeforeTheNextItemEvenWithRepeat() {
        assumeTrue(BuildConfig.BETA)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val file=File(context.cacheDir,"beta-sleep-${System.nanoTime()}.wav")
        val sampleRate=8000
        val bytes=sampleRate*2*12
        val header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(bytes+36).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate*2).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(bytes).array()
        file.outputStream().use { it.write(header); it.write(ByteArray(bytes)) }
        var player: ExoPlayer?=null
        var tools: PlaybackBetaTools?=null
        val uri=file.toURI().toString()
        fun waitFor(predicate: ()->Boolean) {
            val end=SystemClock.elapsedRealtime()+10000
            while(SystemClock.elapsedRealtime() < end) {
                var done=false
                instrumentation.runOnMainSync { done=predicate() }
                if(done) return
                SystemClock.sleep(40)
            }
            fail("Playback condition timed out")
        }
        try {
            instrumentation.runOnMainSync {
                LibraryExtras.init(context)
                player=ExoPlayer.Builder(context).build().apply {
                    volume=0f
                    setMediaItems(listOf(LibraryEntry(uri,"first","audio/wav",12000).mediaItem(),
                        LibraryEntry(uri+"#second","second","audio/wav",12000).mediaItem()))
                    prepare()
                }
                tools=PlaybackBetaTools(player!!)
            }
            waitFor { player!!.playbackState == Player.STATE_READY }
            instrumentation.runOnMainSync { player!!.seekTo(6000) }
            waitFor { LibraryExtras.position(uri,12000) >= 5900 }
            instrumentation.runOnMainSync {
                player!!.repeatMode=Player.REPEAT_MODE_ALL
                PlaybackBetaRuntime.setTimer!!.invoke(-1)
                assertTrue(player!!.pauseAtEndOfMediaItems)
                player!!.seekTo(11000); player!!.play()
            }
            waitFor { !player!!.playWhenReady && !PlaybackBetaRuntime.timer.value.active }
            instrumentation.runOnMainSync {
                assertEquals(uri,player!!.currentMediaItem!!.mediaId)
                assertEquals(0L,LibraryExtras.position(uri,12000))
            }
        } finally {
            instrumentation.runOnMainSync { tools?.close(); player?.release() }
            context.getSharedPreferences("playback_bookmarks",0).edit().remove(uri).remove(uri+"#second").commit()
            file.delete()
        }
    }
}
