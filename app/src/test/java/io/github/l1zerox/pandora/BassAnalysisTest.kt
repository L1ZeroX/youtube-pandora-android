package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

internal fun bassTone(rate: Int = 48_000, frequency: Double = 100.0, amplitude: Double = .25,
    channels: Int = 1, seconds: Double = 1.0, encoding: BassPcm = BassPcm.S16, opposite: Boolean = false): ByteBuffer {
    val frames=(rate*seconds).toInt()
    val buffer=ByteBuffer.allocate(frames*channels*encoding.bytes).order(if(encoding.bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
    repeat(frames) { frame -> repeat(channels) { channel ->
        val value=sin(2*PI*frequency*frame/rate)*amplitude*if(opposite && channel%2 == 1) -1 else 1
        when(encoding) {
            BassPcm.U8 -> buffer.put((value*127+128).toInt().toByte())
            BassPcm.S16,BassPcm.S16_BE -> buffer.putShort((value*32767).toInt().toShort())
            BassPcm.S24,BassPcm.S24_BE -> {
                val sample=(value*8388607).toInt()
                for(shift in if(encoding.bigEndian) listOf(16,8,0) else listOf(0,8,16)) buffer.put((sample shr shift).toByte())
            }
            BassPcm.S32,BassPcm.S32_BE -> buffer.putInt((value*2147483647).toInt())
            BassPcm.F32,BassPcm.F32_BE -> buffer.putFloat(value.toFloat())
            BassPcm.F64,BassPcm.F64_BE -> buffer.putDouble(value)
        }
    } }
    buffer.flip(); return buffer
}

class BassAnalysisTest {
    private fun measured(rate: Int = 48_000, frequency: Double = 100.0, amplitude: Double = .25,
        channels: Int = 1, encoding: BassPcm = BassPcm.S16, opposite: Boolean = false): List<BassPoint> {
        val result=mutableListOf<BassPoint>()
        BassDetector(rate,channels,encoding).read(bassTone(rate,frequency,amplitude,channels,encoding=encoding,opposite=opposite),0,result::add)
        return result
    }
    @Test fun hundredHertzRespondsMoreStronglyThanSubBassOrTrebleAtCommonSampleRates() {
        for(rate in listOf(8_000,44_100,48_000,96_000)) {
            assertTrue("100 Hz at $rate",measured(rate).takeLast(5).map { it.level }.average() > .8)
            assertTrue("1 kHz at $rate",measured(rate,1000.0).takeLast(5).all { it.level < .05f })
            assertTrue("20 Hz at $rate",measured(rate,20.0).takeLast(5).all { it.level < .15f })
        }
    }
    @Test fun silenceDcAndNonFiniteSamplesDoNotBecomeFakeBeats() {
        assertTrue(measured(amplitude=0.0).all { it.level == 0f })
        val detector=BassDetector(48_000,1,BassPcm.F32)
        val invalid=ByteBuffer.allocate(48_000*4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(48_000) { invalid.putFloat(if(it%2 == 0) Float.NaN else Float.POSITIVE_INFINITY) }; invalid.flip()
        val points=mutableListOf<BassPoint>(); detector.read(invalid,0,points::add)
        assertTrue(points.all { it.level == 0f })
        val dc=ByteBuffer.allocate(48_000*4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(48_000) { dc.putFloat(.5f) }; dc.flip(); points.clear(); detector.reset(); detector.read(dc,0,points::add)
        assertTrue(points.takeLast(5).all { it.level == 0f })
    }
    @Test fun antiphaseStereoIsAsStrongAsMonoAndQuieterBassIsSmaller() {
        val mono=measured(amplitude=.1).map { it.level }
        val stereo=measured(amplitude=.1,channels=2,opposite=true).map { it.level }
        mono.zip(stereo).forEach { assertEquals(it.first,it.second,.0001f) }
        assertTrue(measured(amplitude=.01).last().level < mono.last())
    }
    @Test fun chunkBoundariesPreserveFilterStateTimestampsAndOriginalBytes() {
        val source=bassTone()
        val original=source.array().clone()
        val expected=measured()
        val actual=mutableListOf<BassPoint>(); val detector=BassDetector(48_000,1,BassPcm.S16)
        var offset=0
        while(offset < source.limit()) {
            val end=minOf(offset+2048,source.limit())
            val part=source.duplicate().apply { position(offset); limit(end) }
            detector.read(part,(offset/2)*1_000_000L/48_000,actual::add)
            assertEquals(offset,part.position()); assertEquals(end,part.limit()); offset=end
        }
        assertEquals(expected.size,actual.size)
        expected.zip(actual).forEach { (a,b) -> assertEquals(a.level,b.level,.0001f); assertTrue(kotlin.math.abs(a.timeUs-b.timeUs) <= 1) }
        assertArrayEquals(original,source.array()); assertEquals(0,source.position())
        detector.reset(); actual.clear(); detector.read(bassTone(amplitude=0.0),0,actual::add)
        assertTrue(actual.all { it.level == 0f })
    }
    @Test fun integerFloatAndBothByteOrdersRecognizeTheSameSignal() {
        for(encoding in BassPcm.entries) {
            val levels=measured(encoding=encoding)
            assertEquals(25,levels.size); assertEquals(1_000_000L,levels.last().timeUs)
            assertEquals(measured().last().level,levels.last().level,.03f)
        }
    }
    @Test fun timelineNeverShowsFutureSamplesAndClearsStaleSeekAndOverflowData() {
        val timeline=BassTimeline(3)
        val base=1_000_000_000_000L
        timeline.add(BassPoint(base+40_000,.8f)); timeline.add(BassPoint(base+80_000,.2f))
        assertEquals(0f,timeline.levelAt(base),0f)
        assertEquals(.8f,timeline.levelAt(base+40_000),0f); assertEquals(.8f,timeline.levelAt(base+79_999),0f)
        assertEquals(.2f,timeline.levelAt(base+80_000),0f); assertEquals(0f,timeline.levelAt(base+300_001),0f)
        assertEquals(0f,timeline.levelAt(base),0f)
        for(i in 1..4) timeline.add(BassPoint(i*1000L,i/10f))
        assertEquals(0f,timeline.levelAt(1000),0f); assertEquals(.2f,timeline.levelAt(2000),0f)
        timeline.clear(); assertEquals(0f,timeline.levelAt(4000),0f)
    }
    @Test fun bassSpeedVariesWithinBoundsWithoutChangingTheSliderEndpoint() {
        assertEquals(4000,bassCycleMillis(0f)); assertEquals(400,bassCycleMillis(1f))
        assertTrue(bassCycleMillis(.8f) < bassCycleMillis(.2f))
        for(level in listOf(Float.NaN,Float.POSITIVE_INFINITY,-1f,0f,.4f,1f,2f)) {
            assertTrue(bassCycleMillis(level) in 400..4000)
            assertEquals(0f,playbackWaveOffset(0f,300f,48f,9f,.3f),0f)
            assertEquals(0f,playbackWaveOffset(300f,300f,48f,9f,.3f),0f)
        }
        assertEquals(PlaybackEffect.BASS,PlaybackEffect.fromId("bass_100hz"))
        assertEquals("wave",Preferences().libraryPlayerEffect)
    }
}
