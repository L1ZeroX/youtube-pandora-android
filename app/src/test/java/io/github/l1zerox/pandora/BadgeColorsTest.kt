package io.github.l1zerox.pandora

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.*
import org.junit.Test

class BadgeColorsTest {
    @Test fun translucentLabelsRemainReadableOverBrightThumbnails() {
        for(red in 0..255 step 51) for(green in 0..255 step 51) for(blue in 0..255 step 51) {
            val tint=readableGlassTint(Color(red,green,blue))
            assertEquals(.68f,tint.alpha,.005f)
            assertTrue(1.05f/(tint.compositeOver(Color.White).luminance()+.05f) >= 4.5f)
        }
    }
    @Test fun imageTintsKeepWhiteTextReadableAndSectionHeadingsDarker() {
        for(red in 0..255 step 51) for(green in 0..255 step 51) for(blue in 0..255 step 51) {
            val source=Color(red,green,blue,80)
            val standard=readableBadgeColor(source)
            val strong=strongBadgeColor(source)
            assertEquals(1f,standard.alpha,0f)
            assertTrue(1.05f/(standard.luminance()+.05f) >= 4.5f)
            assertTrue(strong.luminance() <= standard.luminance())
            assertTrue(1.05f/(strong.luminance()+.05f) >= 4.5f)
        }
    }

    @Test fun readableImageTintsRetainTheirOriginalColor() {
        val green=Color(0xFF306D49)
        val purple=Color(0xFF795780)
        assertEquals(green,readableBadgeColor(green))
        assertEquals(purple,readableBadgeColor(purple))
        assertNotEquals(readableBadgeColor(green),readableBadgeColor(purple))
    }
}
