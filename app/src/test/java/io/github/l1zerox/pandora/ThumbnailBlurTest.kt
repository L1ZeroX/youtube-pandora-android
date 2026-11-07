package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class ThumbnailBlurTest {
    @Test fun tinyAndUniformImagesKeepTheirColorAndAlpha() {
        for(size in listOf(1 to 1,1 to 7,7 to 1,15 to 9)) {
            val pixels=IntArray(size.first*size.second) { 0xA0356C91.toInt() }
            blurThumbnailPixels(pixels,size.first,size.second)
            assertTrue(pixels.all { it == 0xA0356C91.toInt() })
        }
    }
    @Test fun hardImageEdgesSoftenWithoutChangingOpacityOrDistantColors() {
        val width=40; val height=12
        val pixels=IntArray(width*height) { if(it%width < width/2) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        blurThumbnailPixels(pixels,width,height)
        assertTrue(pixels.all { it ushr 24 == 255 })
        assertEquals(0xFF000000.toInt(),pixels[0])
        assertEquals(0xFFFFFFFF.toInt(),pixels.last())
        val left=pixels[width*6+19] and 255
        val right=pixels[width*6+20] and 255
        assertTrue(left in 1..254); assertTrue(right in 1..254); assertTrue(left < right)
    }
}
