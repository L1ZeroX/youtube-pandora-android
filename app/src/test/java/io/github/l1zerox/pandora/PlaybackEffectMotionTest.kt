package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

class PlaybackEffectMotionTest {
    @Test fun barsMoveThroughIntermediateHeightsBetweenAudioUpdatesInsteadOfJumping() {
        val motion=PlaybackEffectMotion(PlaybackEffect.BARS,true)
        val target=List(32) { if(it%2 == 0) .8f else .2f }
        val first=FloatArray(32)
        for(frame in 1..12) {
            assertTrue(motion.advance(1.0/120,0f,target))
            for(i in target.indices) {
                assertTrue(motion.spectrum[i] > first[i])
                assertTrue(motion.spectrum[i] < target[i])
                first[i]=motion.spectrum[i]
            }
        }
        assertEquals((.8*(1-exp(-.1/.035))).toFloat(),motion.spectrum[0],.00001f)
        val high=motion.spectrum[0]
        motion.advance(1.0/120,0f,emptyList())
        assertTrue(motion.spectrum[0] > 0f && motion.spectrum[0] < high)
    }
    @Test fun theSameElapsedTimeLooksTheSameAt60_90And120Hertz() {
        val heights=listOf(60,90,120).map { rate ->
            val motion=PlaybackEffectMotion(PlaybackEffect.BARS,true)
            repeat(rate/10) { motion.advance(1.0/rate,0f,List(32) { .75f }) }
            motion.spectrum[12]
        }
        heights.forEach { assertEquals(heights.first(),it,.00001f) }
    }
    @Test fun changingAudioTargetsNeverRestartsTheWavePhaseOrMakesItRunBackwards() {
        for(effect in listOf(PlaybackEffect.NOTIFICATION,PlaybackEffect.BASS)) {
            val motion=PlaybackEffectMotion(effect,true)
            val period=if(effect == PlaybackEffect.NOTIFICATION) 5.0 else 1.0
            var previous=0.0
            repeat(600) { frame ->
                motion.advance(1.0/120,if(frame%6 < 3) .2f else .9f,emptyList())
                val distance=(motion.phase-previous+period)%period
                assertTrue("phase step for $effect: $distance",distance > 0 && distance <= 2.5/120+.00001)
                previous=motion.phase
            }
        }
    }
    @Test fun fixedWaveAndNotificationStylesKeepTheirOriginalSpeedsAndLoopSeamlessly() {
        for((effect,seconds,period) in listOf(Triple(PlaybackEffect.WAVE,1.4,1.0),Triple(PlaybackEffect.NOTIFICATION,18.0,5.0))) {
            val motion=PlaybackEffectMotion(effect,false)
            repeat((seconds*100).toInt()) { motion.advance(.01,1f,emptyList()) }
            assertTrue(min(motion.phase,period-motion.phase) < .00001)
            assertEquals(0f,motion.bass,0f)
        }
    }
    @Test fun irregularFramesAndResumeGapsCannotCauseLargeJumpsOrNonFiniteGeometry() {
        val motion=PlaybackEffectMotion(PlaybackEffect.BASS,true)
        for(dt in listOf(.008,.017,.033,2.0,Double.NaN,Double.POSITIVE_INFINITY,-.1)) {
            val previous=motion.phase
            motion.advance(dt,Float.NaN,listOf(Float.POSITIVE_INFINITY))
            assertTrue(motion.phase.isFinite()); assertTrue(motion.phase-previous in 0.0.. .125)
            assertTrue(motion.bass.isFinite())
        }
        val bars=PlaybackEffectMotion(PlaybackEffect.BARS,true)
        bars.advance(2.0,0f,List(32) { 1f })
        assertTrue(bars.spectrum.all { it > 0f && it < .8f }); bars.clear()
        repeat(20) { bars.advance(.016,1f,listOf(Float.NaN,2f,-1f)) }
        assertTrue(bars.spectrum.all { it.isFinite() && it in 0f..1f })
        assertEquals(0f,bars.spectrum[0],0f); assertEquals(0f,bars.spectrum[2],0f)
    }
    @Test fun pauseClearsLevelsAndTheFrameBufferIsReusedInsteadOfAllocating32Animations() {
        val motion=PlaybackEffectMotion(PlaybackEffect.BARS,true)
        val buffer=motion.spectrum
        repeat(100) { motion.advance(.016,1f,List(32) { .8f }); assertSame(buffer,motion.spectrum) }
        assertTrue(motion.active); assertTrue(motion.clear()); assertFalse(motion.active)
        assertTrue(motion.spectrum.all { it == 0f }); assertFalse(motion.clear())
        assertFalse(motion.advance(0.0,1f,emptyList())); assertFalse(motion.active)
    }
    @Test fun rapidlyChangingBarsStayBoundedAndReachSilenceWithoutOvershootOrEndlessTinyUpdates() {
        val motion=PlaybackEffectMotion(PlaybackEffect.BARS,true)
        var previous=0f
        repeat(120) { frame ->
            val target=if(frame%3 == 0) 1f else 0f
            motion.advance(1.0/60,0f,List(32) { target })
            assertTrue(abs(motion.spectrum[0]-previous) < .4f)
            assertTrue(motion.spectrum.all { it in 0f..1f }); previous=motion.spectrum[0]
        }
        repeat(60) { motion.advance(1.0/60,0f,emptyList()) }
        assertTrue(motion.spectrum.all { it == 0f })
        assertFalse(motion.advance(1.0/60,0f,emptyList()))
    }
}
