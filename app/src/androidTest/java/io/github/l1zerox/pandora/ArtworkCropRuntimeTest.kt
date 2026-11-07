package io.github.l1zerox.pandora

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class ArtworkCropRuntimeTest {
    @Test fun savedJpegContainsOnlyTheChosenRegion() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=Bitmap.createBitmap(200,100,Bitmap.Config.ARGB_8888)
        Canvas(source).apply {
            drawColor(Color.RED)
            drawRect(100f,0f,200f,100f,Paint().apply { color=Color.BLUE })
        }
        try {
            for((crop,color,size) in listOf(Triple(ArtworkCrop(1f,.75f,.5f),Color.BLUE,100),
                Triple(ArtworkCrop(2f,.25f,.5f),Color.RED,50))) {
                val file=saveCroppedArtwork(context,source,crop)
                try {
                    val image=BitmapFactory.decodeFile(file.absolutePath)
                    try {
                        assertEquals(size,image.width); assertEquals(size,image.height)
                        val pixel=image.getPixel(size/2,size/2)
                        assertTrue(kotlin.math.abs(Color.red(pixel)-Color.red(color)) < 5)
                        assertTrue(kotlin.math.abs(Color.blue(pixel)-Color.blue(color)) < 5)
                    } finally { image.recycle() }
                } finally { file.delete() }
            }
        } finally { source.recycle() }
    }
}
