package io.github.l1zerox.pandora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test

class PlaybackMotionDeviceTest {
    @get:Rule val screen=createComposeRule()

    @Test fun heldAudioTargetsContinueToAnimateOnFramesWithoutMovingTheSeekMarker() {
        assumeFalse(PlaybackRuntime.state.value.playing)
        val original=BassRuntime.spectrum.value
        var playing by mutableStateOf(true)
        screen.mainClock.autoAdvance=false
        try {
            BassRuntime.spectrum.value=List(32) { 0f }
            screen.setContent { MaterialTheme {
                Column(Modifier.width(320.dp).background(Color.Black)) {
                    PlaybackPositionSlider("a",.5f,{fail("Animation must not seek")},{},true,Color.Red,"位置テスト",
                        Modifier.fillMaxWidth(),compact=true,waveform=true,playing=playing,effect=PlaybackEffect.BARS)
                }
            } }
            repeat(3) { screen.mainClock.advanceTimeByFrame() }
            val track=screen.onNodeWithTag("playback-position-track",useUnmergedTree=true)
            fun upperBarPixels(): Int {
                val pixels=track.captureToImage().toPixelMap()
                var count=0
                for(x in pixels.width/20 until pixels.width*2/5) for(y in 0 until (pixels.height*.38f).toInt())
                    if(pixels[x,y].red > .1f) count++
                return count
            }
            val marker=screen.onNodeWithTag("playback-position-marker",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            val flat=upperBarPixels()
            screen.runOnIdle { BassRuntime.spectrum.value=List(32) { 1f } }
            screen.mainClock.advanceTimeByFrame()
            val first=upperBarPixels()
            screen.mainClock.advanceTimeBy(80)
            val later=upperBarPixels()
            assertTrue(first > flat); assertTrue(later > first)
            assertEquals(marker,screen.onNodeWithTag("playback-position-marker",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot)
            screen.runOnIdle { playing=false }
            screen.mainClock.advanceTimeByFrame()
            assertEquals(flat,upperBarPixels())
        } finally { BassRuntime.spectrum.value=original }
    }
}
