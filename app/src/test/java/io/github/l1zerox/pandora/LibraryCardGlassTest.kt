package io.github.l1zerox.pandora

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class LibraryCardGlassTest {
    @Test fun actualDarkWallpaperAllowsMoreTransparencyWhileKeepingTextReadable() {
        val backgrounds=listOf(Color(0xFF152030),Color(0xFF354050))
        for(source in listOf(Color.Black,Color.Red,Color.Blue)) {
            val palette=libraryPalette(source,true)
            val tint=libraryGlassTint(palette,backgrounds)
            assertTrue(tint.alpha < libraryGlassTint(palette).alpha)
            for(background in backgrounds) {
                assertTrue(libraryContrast(palette.content,tint.compositeOver(background)) >= 4.5f)
                assertTrue(libraryContrast(palette.detail,tint.compositeOver(background)) >= 4.5f)
            }
        }
    }
    @Test fun glassRetainsExistingColorsAndTextContrastOverDifferentWallpapers() {
        for(dark in listOf(false,true)) for(r in 0..255 step 51) for(g in 0..255 step 51) for(b in 0..255 step 51) {
            val palette=libraryPalette(Color(r,g,b),dark)
            val tint=libraryGlassTint(palette)
            assertEquals(palette.surface.red,tint.red,.000001f)
            assertEquals(palette.surface.green,tint.green,.000001f)
            assertEquals(palette.surface.blue,tint.blue,.000001f)
            for(background in listOf(Color.Black,Color.White,Color.Red,Color.Green,Color.Blue)) {
                assertTrue(libraryContrast(palette.content,tint.compositeOver(background)) >= 4.5f)
                assertTrue(libraryContrast(palette.detail,tint.compositeOver(background)) >= 4.5f)
            }
        }
    }
    @Test fun darkCardsBecomeTranslucentWithoutChangingTheirPalette() {
        for(source in listOf(Color.Black,Color.Red,Color.Blue)) {
            val palette=libraryPalette(source,true)
            assertTrue(libraryGlassTint(palette).alpha < 1f)
            assertEquals(Color.White,palette.content)
        }
    }
    @Test fun wideWallpaperKeepsItsCenterCropInsideAPortraitCard() {
        val (offset,size)=libraryBackdropPlacement(IntSize(400,200),Rect(0f,0f,300f,600f),Offset(0f,100f))
        assertEquals(IntSize(1200,600),size)
        assertEquals(IntOffset(-450,-100),offset)
    }
    @Test fun scrollingMovesTheGlassWindowAcrossTheSameStationaryWallpaper() {
        val viewport=Rect(10f,20f,310f,620f)
        val first=libraryBackdropPlacement(IntSize(200,400),viewport,Offset(30f,220f))
        val scrolled=libraryBackdropPlacement(IntSize(200,400),viewport,Offset(30f,170f))
        assertEquals(first.second,scrolled.second)
        assertEquals(IntOffset(-20,-200),first.first)
        assertEquals(first.first.y+220,scrolled.first.y+170)
        assertEquals(first.first.x,scrolled.first.x)
    }
}
