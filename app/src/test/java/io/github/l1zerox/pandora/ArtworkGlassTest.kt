package io.github.l1zerox.pandora

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.*
import org.junit.Test

class ArtworkGlassTest {
    @Test fun glassKeepsWhiteTextReadableOverBlackWhiteAndColoredImages() {
        for(r in 0..255 step 51) for(g in 0..255 step 51) for(b in 0..255 step 51) {
            val tint=artworkGlassTint(Color(r,g,b))
            assertTrue(tint.alpha < 1f)
            for(background in listOf(Color.White,Color.Black,Color.Red,Color.Green,Color.Blue))
                assertTrue(libraryContrast(Color.White,tint.compositeOver(background)) >= 4.5f)
        }
    }
    @Test fun progressAndThumbStayVisibleOnGlass() {
        for(r in 0..255 step 51) for(g in 0..255 step 51) for(b in 0..255 step 51) {
            val source=Color(r,g,b)
            val accent=artworkGlassAccent(source)
            for(background in listOf(Color.White,Color.Black))
                assertTrue(libraryContrast(accent,artworkGlassTint(source).compositeOver(background)) >= 3f)
        }
    }
    @Test fun libraryUsesDownloadBackgroundEvenWhenActiveArtworkChanges() {
        for(dark in listOf(false,true)) for(source in listOf(null,Color.Black,Color.White,Color.Red,Color.Green,Color.Blue)) {
            val background=appBackgroundColor(dark)
            val colors=libraryColorScheme(source,dark,background)
            assertEquals(background,colors.background)
            assertTrue(libraryContrast(colors.primary,background) >= 4.5f)
        }
    }
}
