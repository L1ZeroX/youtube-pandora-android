package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ArtworkCropTest {
    @Test fun initialCropIsCenteredSquareForPortraitAndLandscape() {
        assertEquals(CropBounds(500,0,1000),artworkCropBounds(ArtworkCrop(),2000,1000))
        assertEquals(CropBounds(0,500,1000),artworkCropBounds(ArtworkCrop(),1000,2000))
        assertEquals(CropBounds(0,0,512),artworkCropBounds(ArtworkCrop(),512,512))
    }
    @Test fun zoomAndPositionProduceTheExpectedPixelRegion() {
        assertEquals(CropBounds(1250,250,500),artworkCropBounds(ArtworkCrop(2f,.75f,.5f),2000,1000))
    }
    @Test fun draggingMovesImageAndNeverRevealsAnEmptyArea() {
        val moved=transformArtworkCrop(ArtworkCrop(),2000,1000,.25f,0f,1f)
        assertEquals(CropBounds(250,0,1000),artworkCropBounds(moved,2000,1000))
        assertEquals(CropBounds(0,0,1000),artworkCropBounds(transformArtworkCrop(moved,2000,1000,9f,9f,1f),2000,1000))
        assertEquals(CropBounds(1000,0,1000),artworkCropBounds(transformArtworkCrop(moved,2000,1000,-9f,-9f,1f),2000,1000))
    }
    @Test fun pinchKeepsTheSourcePointUnderTheFingers() {
        val old=ArtworkCrop(2f,.5f,.5f)
        val changed=transformArtworkCrop(old,2000,1000,0f,0f,2f,.8f,.3f)
        val sourceXBefore=old.centerX*2000+(.8f-.5f)*500
        val sourceXAfter=changed.centerX*2000+(.8f-.5f)*250
        val sourceYBefore=old.centerY*1000+(.3f-.5f)*500
        val sourceYAfter=changed.centerY*1000+(.3f-.5f)*250
        assertEquals(sourceXBefore,sourceXAfter,.001f)
        assertEquals(sourceYBefore,sourceYAfter,.001f)
    }
    @Test fun invalidAndExtremeValuesAreClamped() {
        assertEquals(ArtworkCrop(),boundedCrop(ArtworkCrop(Float.NaN,Float.NaN,Float.POSITIVE_INFINITY),100,100))
        assertEquals(5f,boundedCrop(ArtworkCrop(99f),100,100).zoom,0f)
        assertEquals(CropBounds(0,0,1),artworkCropBounds(ArtworkCrop(5f,0f,1f),1,1))
    }
    @Test fun randomCropsAlwaysStayInsideTheImageAndRemainSquare() {
        val random=Random(42)
        repeat(2000) {
            val width=random.nextInt(1,4097); val height=random.nextInt(1,4097)
            val crop=ArtworkCrop(random.nextFloat()*10,random.nextFloat()*4-2,random.nextFloat()*4-2)
            val rect=artworkCropBounds(crop,width,height)
            assertTrue(rect.size >= 1)
            assertTrue(rect.left >= 0 && rect.top >= 0)
            assertTrue(rect.left+rect.size <= width && rect.top+rect.size <= height)
        }
    }
}
