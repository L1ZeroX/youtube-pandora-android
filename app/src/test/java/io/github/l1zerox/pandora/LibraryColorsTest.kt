package io.github.l1zerox.pandora

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class LibraryColorsTest {
    @Test fun neutralAndTransparentArtworkDoesNotInventAColor() {
        assertEquals(Color.Black,libraryThumbnailColor(IntArray(1024) { 0xFF000000.toInt() }))
        assertEquals(Color.White,libraryThumbnailColor(IntArray(1024) { 0xFFFFFFFF.toInt() }))
        assertEquals(Color(0xFF888888),libraryThumbnailColor(IntArray(1024) { 0xFF888888.toInt() }))
        assertNull(libraryThumbnailColor(intArrayOf(0x00FF0000,0x00000000)))
        assertNull(libraryThumbnailColor(intArrayOf()))
    }
    @Test fun coloredArtworkWinsOverBlackLetterboxingAndTransparentPixels() {
        val pixels=IntArray(1000) { if(it < 300) 0xFF00A060.toInt() else 0xFF000000.toInt() }
        assertEquals(Color(0xFF00A060),libraryThumbnailColor(pixels))
        assertEquals(Color(0xFF0055BB),libraryThumbnailColor(intArrayOf(0xFF0055BB.toInt(),0x00FF0000)))
    }
    @Test fun cardTitleDetailsAndPlayIconStayReadableForBothThemes() {
        for(dark in listOf(false,true)) for(r in 0..255 step 17) for(g in 0..255 step 17) for(b in 0..255 step 17) {
            val palette=libraryPalette(Color(r,g,b),dark)
            assertEquals(1f,palette.surface.alpha,0f)
            assertTrue("Title: $r $g $b / $dark",libraryContrast(palette.surface,palette.content) >= 4.5f)
            assertTrue("Detail: $r $g $b / $dark",libraryContrast(palette.surface,palette.detail) >= 4.5f)
        }
    }
    @Test fun activeArtworkKeepsHeadersButtonsAndSelectedFiltersReadable() {
        for(dark in listOf(false,true)) for(r in 0..255 step 51) for(g in 0..255 step 51) for(b in 0..255 step 51) {
            val scheme=libraryColorScheme(Color(r,g,b),dark)
            val pairs=listOf(scheme.background to scheme.onBackground,scheme.surface to scheme.onSurface,
                scheme.background to scheme.primary,scheme.primary to scheme.onPrimary,
                scheme.primaryContainer to scheme.onPrimaryContainer,scheme.secondaryContainer to scheme.onSecondaryContainer,
                scheme.background to scheme.onSurfaceVariant)
            pairs.forEach { (background,text) -> assertTrue("Theme: $r $g $b / $dark",libraryContrast(background,text) >= 4.5f) }
        }
    }
    @Test fun blackAndWhiteSurfacesChooseTheCorrectTextAndColorsRemainDistinct() {
        assertEquals(Color.White,libraryTextColor(Color.Black))
        assertEquals(Color.Black,libraryTextColor(Color.White))
        for(dark in listOf(false,true)) {
            assertNotEquals(libraryPalette(Color.Red,dark).surface,libraryPalette(Color.Blue,dark).surface)
            val fallback=libraryPalette(null,dark)
            assertTrue(libraryContrast(fallback.surface,fallback.content) >= 4.5f)
        }
    }
}
