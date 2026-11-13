package io.github.l1zerox.pandora

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.*
import org.junit.Test

class PlaybackAccentTest {
    @Test fun musicGradientAndControlsKeepContrastForEveryArtworkPalette() {
        for(artwork in listOf(false,true)) for(r in 0..255 step 51) for(g in 0..255 step 51) for(b in 0..255 step 51) {
            val palette=musicPalette(Color(r,g,b),artworkBackground=artwork)
            val backgrounds=listOf(palette.top,palette.bottom)+if(artwork)
                listOf(Color.Black,Color.Black.copy(alpha=MUSIC_ARTWORK_DIM).compositeOver(Color.White)) else emptyList()
            for(background in backgrounds) {
                assertTrue(libraryContrast(Color.White,background) >= 4.5f)
                assertTrue(libraryContrast(palette.secondary,background) >= 4.5f)
                assertTrue(libraryContrast(palette.accent,background) >= 3f)
            }
            assertTrue(libraryContrast(palette.onAccent,palette.accent) >= 4.5f)
        }
    }
    @Test fun artworkAccentRemainsReadableForDarkWhiteAndSaturatedImages() {
        for(r in 0..255 step 51) for(g in 0..255 step 51) for(b in 0..255 step 51) {
            val accent=playbackAccent(Color(r,g,b))
            assertTrue(libraryContrast(accent,Color.Black) >= 3f)
            assertTrue(libraryContrast(accent,Color(0xFF242424)) >= 3f)
            assertEquals(1f,accent.alpha,0f)
        }
    }
    @Test fun readableArtworkColorsAreRetainedAndNeutralArtworkStaysNeutral() {
        assertEquals(Color.Green,playbackAccent(Color.Green))
        assertEquals(Color.White,playbackAccent(Color.White))
        val black=playbackAccent(Color.Black)
        assertEquals(black.red,black.green,0f); assertEquals(black.red,black.blue,0f)
        assertNotNull(playbackAccent(null))
    }
}
