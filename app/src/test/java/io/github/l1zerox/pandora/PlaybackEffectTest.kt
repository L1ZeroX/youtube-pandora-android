package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class PlaybackEffectTest {
    @Test fun oldAndUnknownPreferencesKeepTheOriginalWave() {
        assertEquals(PlaybackEffect.WAVE,PlaybackEffect.fromId(""))
        assertEquals(PlaybackEffect.WAVE,PlaybackEffect.fromId("unknown"))
        assertEquals(PlaybackEffect.NOTIFICATION,PlaybackEffect.fromId("notification"))
        assertEquals(PlaybackEffect.BASS,PlaybackEffect.fromId("bass_100hz"))
        assertEquals("bass_80hz",PlaybackEffect.fromId("bass_100hz").id)
        assertEquals("wave",Preferences().libraryPlayerEffect)
    }
    @Test fun notificationBassCanBeDisabledWithoutDisablingTheDedicatedBassStyle() {
        assertTrue(Preferences().libraryNotificationBass)
        for(style in PlaybackEffect.entries) for(wave in listOf(false,true)) for(link in listOf(false,true)) {
            val p=Preferences(libraryPlayerWave=wave,libraryPlayerEffect=style.id,libraryNotificationBass=link)
            assertEquals(wave && (style == PlaybackEffect.BARS || style == PlaybackEffect.BASS || style == PlaybackEffect.NOTIFICATION && link),playbackBassEnabled(p))
        }
    }
    @Test fun filledCrestsNeverMoveThePlaybackEndpointOrGoBelowTheBaseline() {
        for(width in listOf(1f,10f,400f)) for(phase in listOf(0f,.3f,4.8f)) {
            assertEquals(0f,notificationWaveHeight(0f,width,96f,9f,phase),0f)
            assertEquals(0f,notificationWaveHeight(width,width,96f,9f,phase),0f)
            for(gain in listOf(0f,.2f,.7f,1f)) for(i in 0..100)
                assertTrue(notificationWaveHeight(width*i/100,width,96f,9f*gain,phase) in 0f..9f*gain)
        }
    }
    @Test fun layersLoopSeamlesslyAtFiveAndFourCyclesAndFlattenWhenPaused() {
        for(x in 1..300) {
            assertEquals(notificationWaveHeight(x.toFloat(),400f,96f,9f,0f),notificationWaveHeight(x.toFloat(),400f,96f,9f,5f),.0001f)
            assertEquals(notificationWaveHeight(x.toFloat(),400f,72f,6f,.35f),notificationWaveHeight(x.toFloat(),400f,72f,6f,4.35f),.0001f)
            assertEquals(0f,notificationWaveHeight(x.toFloat(),400f,96f,0f,.2f),0f)
        }
        assertNotEquals(notificationWaveHeight(50f,400f,96f,9f,0f),notificationWaveHeight(50f,400f,96f,9f,.3f))
    }
    @Test fun invalidGeometryCannotProduceNonFiniteDrawingCoordinates() {
        for(value in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY)) {
            assertEquals(0f,notificationWaveHeight(value,100f,96f,9f,0f),0f)
            assertEquals(0f,notificationWaveHeight(20f,value,96f,9f,0f),0f)
            assertEquals(0f,notificationWaveHeight(20f,100f,96f,9f,value),0f)
        }
    }
}
