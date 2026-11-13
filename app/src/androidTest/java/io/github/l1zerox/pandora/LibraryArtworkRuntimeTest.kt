package io.github.l1zerox.pandora

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LibraryArtworkRuntimeTest {
    @Test fun croppedOverridesPersistAndResetToAlbumWithoutRewritingTheSource() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="Test-${UUID.randomUUID()}"
        val entry=LibraryEntry("content://artwork-test/$name","Song","audio/mpeg",album=name)
        val albumKey=libraryArtworkKeys(entry,LibraryCollections()).last()
        val trackKey=trackArtworkKey(entry.uri)
        val bitmap=Bitmap.createBitmap(200,100,Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.BLUE)
            LibraryArtwork.set(albumKey,saveCroppedArtwork(context,bitmap,ArtworkCrop()))
            LibraryArtwork.init(context)
            val album=LibraryRepository.artwork(context,entry)!!
            assertEquals(album.width,album.height); assertTrue(Color.blue(album.getPixel(0,0)) > 200)
            bitmap.eraseColor(Color.GREEN)
            LibraryArtwork.set(trackKey,saveCroppedArtwork(context,bitmap,ArtworkCrop(2f)))
            assertTrue(Color.green(LibraryRepository.artwork(context,entry)!!.getPixel(0,0)) > 200)
            LibraryArtwork.set(trackKey,null)
            assertTrue(Color.blue(LibraryRepository.artwork(context,entry)!!.getPixel(0,0)) > 200)
            LibraryArtwork.set(albumKey,null)
            assertNull(LibraryArtwork.file(entry))
        } finally {
            LibraryArtwork.set(trackKey,null); LibraryArtwork.set(albumKey,null); bitmap.recycle()
        }
    }
}
