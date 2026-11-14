package io.github.l1zerox.pandora

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

@androidx.annotation.OptIn(UnstableApi::class)
class BassPlaybackRuntimeTest {
    @Test fun realPcmPlaybackFollowsBassAndResetsOnPauseAndSeekWithoutChangingSpeed() {
        assumeFalse(PlaybackRuntime.state.value.playing)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val original=BassRuntime.enabled
        val file=File(context.cacheDir,"bass-test-${System.nanoTime()}.wav")
        val rate=48_000; val bytes=rate*4*2
        val header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(bytes+36).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(rate).putInt(rate*2).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(bytes).array()
        val pcm=ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN)
        repeat(rate*4) { frame ->
            val frequency=if(frame < rate) 1000.0 else if(frame < rate*3) 80.0 else 0.0
            pcm.putShort((sin(2*PI*frequency*frame/rate)*8191).toInt().toShort())
        }
        file.outputStream().use { it.write(header); it.write(pcm.array()) }
        var player: ExoPlayer?=null
        fun waitFor(condition: ()->Boolean) {
            val end=SystemClock.elapsedRealtime()+10_000
            while(SystemClock.elapsedRealtime() < end) {
                var result=false
                instrumentation.runOnMainSync { result=condition() }
                if(result) return
                SystemClock.sleep(20)
            }
            fail("Bass playback condition timed out")
        }
        try {
            instrumentation.runOnMainSync {
                BassRuntime.enabled=true; BassRuntime.level.value=0f
                player=ExoPlayer.Builder(context,BassRenderersFactory(context)).build().apply {
                    volume=0f
                    setMediaItem(MediaItem.fromUri(file.toURI().toString())); prepare(); play()
                }
            }
            waitFor { player!!.isPlaying && player!!.currentPosition in 300..900 }
            assertTrue(BassRuntime.level.value < .1f)
            waitFor { player!!.currentPosition in 1100..2900 && BassRuntime.level.value > .7f }
            instrumentation.runOnMainSync { assertEquals(1f,player!!.playbackParameters.speed,0f); player!!.pause() }
            waitFor { BassRuntime.level.value == 0f }
            instrumentation.runOnMainSync { player!!.seekTo(3300); player!!.play() }
            waitFor { player!!.currentPosition >= 3400 && BassRuntime.level.value == 0f }
            instrumentation.runOnMainSync { player!!.seekTo(1500) }
            waitFor { player!!.playbackState == Player.STATE_READY && BassRuntime.level.value > .7f }
            instrumentation.runOnMainSync { assertEquals(1f,player!!.playbackParameters.speed,0f) }
        } finally {
            instrumentation.runOnMainSync { player?.release(); BassRuntime.enabled=original; BassRuntime.level.value=0f }
            file.delete()
        }
    }
}
