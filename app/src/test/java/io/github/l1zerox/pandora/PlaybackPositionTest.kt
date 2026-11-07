package io.github.l1zerox.pandora

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class PlaybackPositionTest {
    @Test fun displayedFractionAndSeekTargetAgreeAtStartMiddleAndEnd() {
        for(duration in listOf(1L,263_000L,32_400_000L)) {
            for(position in listOf(0L,1L,16_000L,duration/2,duration-1,duration).filter { it <= duration }) {
                val restored=playbackPositionMs(duration,playbackFraction(position,duration))
                assertTrue("$position/$duration became $restored",abs(position-restored) <= 2)
            }
        }
        assertEquals(16_000L,playbackPositionMs(263_000,playbackFraction(16_000,263_000)))
    }
    @Test fun unknownDurationAndOutOfRangeInputNeverProduceInvalidPositions() {
        for(duration in listOf(0L,-1L,Long.MIN_VALUE)) {
            assertEquals(0f,playbackFraction(16_000,duration),0f)
            assertEquals(0L,playbackPositionMs(duration,.5f))
        }
        assertEquals(0f,playbackFraction(-1,263_000),0f)
        assertEquals(1f,playbackFraction(500_000,263_000),0f)
        assertEquals(0L,playbackPositionMs(263_000,-1f))
        assertEquals(263_000L,playbackPositionMs(263_000,2f))
        for(fraction in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY))
            assertEquals(0L,playbackPositionMs(263_000,fraction))
    }
}
