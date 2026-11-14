package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp

class SpectrumAnalysisTest {
    private fun measured(frequency: Double=80.0, amplitude: Double=.1, rate: Int=48_000,
        channels: Int=1, opposite: Boolean=false, encoding: BassPcm=BassPcm.S16): List<BassPoint> {
        val points=mutableListOf<BassPoint>()
        SpectrumDetector(rate,channels,encoding).read(bassTone(rate,frequency,amplitude,channels,encoding=encoding,opposite=opposite),0,points::add)
        return points
    }
    @Test fun differentFrequenciesMoveDifferentBarsAndBassEmphasisDoesNotInventTreble() {
        for(rate in listOf(8_000,44_100,48_000,96_000)) {
            val bass=measured(rate=rate).last(); val treble=measured(1000.0,rate=rate).last()
            val bassPeak=bass.spectrum.indices.maxBy { bass.spectrum[it] }
            val treblePeak=treble.spectrum.indices.maxBy { treble.spectrum[it] }
            assertEquals(32,bass.spectrum.size)
            assertTrue("bass peak at $rate",bassPeak in 2..10)
            assertTrue("treble peak at $rate",treblePeak > 18)
            assertTrue(bass.spectrum[bassPeak] > .6f)
            assertTrue(bass.level > treble.level+.5f)
            assertTrue(bass.spectrum[treblePeak] < .1f)
        }
    }
    @Test fun fastAttackSlowReleaseAndGainMatchTheProvisionalPreset() {
        assertEquals(1-exp(-1.0),spectrumEnvelope(0f,1f,.020).toDouble(),.00001)
        assertEquals(exp(-1.0),spectrumEnvelope(1f,0f,.200).toDouble(),.00001)
        assertTrue(spectrumEnvelope(0f,1f,.020) > 1-spectrumEnvelope(1f,0f,.020))
        assertEquals(1.7,SpectrumPreset.gain,0.0)
        assertEquals(40.0,SpectrumPreset.bassLow,0.0); assertEquals(160.0,SpectrumPreset.bassHigh,0.0)
        assertTrue(measured(amplitude=.005).last().level < measured(amplitude=.1).last().level)
    }
    @Test fun stereoPhaseCannotCancelTheSpectrumAndPcmFormatsAgree() {
        val mono=measured().last()
        val opposite=measured(channels=2,opposite=true).last()
        mono.spectrum.zip(opposite.spectrum).forEach { (a,b) -> assertEquals(a,b,.0001f) }
        for(encoding in BassPcm.entries) {
            val point=measured(encoding=encoding).last()
            assertEquals(mono.level,point.level,.04f)
            assertTrue(point.spectrum.all { it.isFinite() && it in 0f..1f })
        }
    }
    @Test fun chunkingKeepsSpectralValuesTimestampsAndSourceBytes() {
        val source=bassTone(frequency=440.0); val original=source.array().clone()
        val expected=measured(440.0,amplitude=.25)
        val actual=mutableListOf<BassPoint>(); val detector=SpectrumDetector(48_000,1,BassPcm.S16)
        var offset=0
        while(offset < source.limit()) {
            val end=minOf(offset+2048,source.limit())
            val part=source.duplicate().apply { position(offset); limit(end) }
            detector.read(part,offset/2*1_000_000L/48_000,actual::add)
            assertEquals(offset,part.position()); assertEquals(end,part.limit()); offset=end
        }
        assertEquals(expected.size,actual.size)
        expected.zip(actual).forEach { (a,b) ->
            assertTrue(abs(a.timeUs-b.timeUs) <= 1)
            a.spectrum.zip(b.spectrum).forEach { (x,y) -> assertEquals(x,y,.0001f) }
        }
        assertArrayEquals(original,source.array()); assertEquals(0,source.position())
        detector.reset(); actual.clear(); detector.read(bassTone(amplitude=0.0),0,actual::add)
        assertTrue(actual.all { p -> p.level == 0f && p.spectrum.all { it == 0f } })
    }
    @Test fun silenceAndAliasedHighFrequencyDoNotProduceBassBeats() {
        assertTrue(measured(amplitude=0.0).all { p -> p.level == 0f && p.spectrum.all { it == 0f } })
        assertTrue(measured(7920.0).takeLast(5).all { it.level < .05f })
        assertEquals(0f,spectrumEnvelope(Float.NaN,Float.POSITIVE_INFINITY,.02),0f)
        assertEquals(.4f,spectrumEnvelope(.4f,1f,Double.NaN),0f)
    }
    @Test fun spectrumFollowsTheAudioClockAndDisappearsAfterSeekOrStaleSamples() {
        val timeline=BassTimeline(); val point=measured().first()
        timeline.add(point)
        assertTrue(timeline.pointAt(point.timeUs-1).spectrum.isEmpty())
        assertEquals(point,timeline.pointAt(point.timeUs))
        assertTrue(timeline.pointAt(point.timeUs+200_001).spectrum.isEmpty())
        assertTrue(timeline.pointAt(0).spectrum.isEmpty())
    }
}
