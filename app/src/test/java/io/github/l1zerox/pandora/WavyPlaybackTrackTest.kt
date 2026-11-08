package io.github.l1zerox.pandora

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class WavyPlaybackTrackTest {
    @Test fun endsStayOnTheBaselineAtEveryPhaseIncludingShortProgress() {
        for(width in listOf(0f,1f,7f,28f,320f)) for(phase in listOf(0f,.17f,.5f,.91f,1f)) {
            assertEquals(0f,playbackWaveOffset(0f,width,28f,4f,phase),0f)
            assertEquals(0f,playbackWaveOffset(width,width,28f,4f,phase),0f)
            assertEquals(0f,playbackWaveOffset(width+1,width,28f,4f,phase),0f)
        }
    }
    @Test fun waveStaysWithinItsDrawingHeightAndTapersTowardTheMarker() {
        for(width in listOf(1f,10f,56f,320f)) for(phase in listOf(0f,.2f,.7f,1f)) for(index in 0..100) {
            val x=width*index/100
            val y=playbackWaveOffset(x,width,28f,4f,phase)
            assertTrue(y.isFinite()); assertTrue(abs(y) <= 4f)
            if(width-x < 14f) assertTrue(abs(y) <= 4f*(width-x)/14+.001f)
        }
    }
    @Test fun phaseMovesTheWaveAndCompletesASeamlessCycleWithoutMovingTheEndpoint() {
        val first=playbackWaveOffset(35f,200f,28f,4f,0f)
        val shifted=playbackWaveOffset(35f,200f,28f,4f,.25f)
        assertTrue(abs(first-shifted)>3f)
        assertEquals(first,playbackWaveOffset(35f,200f,28f,4f,1f),.0001f)
        assertEquals(0f,playbackWaveOffset(35f,200f,28f,0f,.5f),0f)
    }
    @Test fun invalidGeometryProducesAFlatTrack() {
        for(invalid in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY)) {
            assertEquals(0f,playbackWaveOffset(invalid,200f,28f,4f,0f),0f)
            assertEquals(0f,playbackWaveOffset(35f,invalid,28f,4f,0f),0f)
            assertEquals(0f,playbackWaveOffset(35f,200f,invalid,4f,0f),0f)
            assertEquals(0f,playbackWaveOffset(35f,200f,28f,invalid,0f),0f)
            assertEquals(0f,playbackWaveOffset(35f,200f,28f,4f,invalid),0f)
        }
        assertEquals(0f,playbackWaveOffset(35f,200f,0f,4f,0f),0f)
        assertEquals(0f,playbackWaveOffset(35f,200f,28f,-4f,0f),0f)
    }
}
