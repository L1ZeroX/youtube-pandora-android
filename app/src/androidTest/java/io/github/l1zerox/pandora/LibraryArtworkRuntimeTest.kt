package io.github.l1zerox.pandora

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LibraryArtworkRuntimeTest {
    @Test fun aCustomAlbumCoverStaysWithItsAlbumAcrossTrackAndAlbumChanges() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="Test-${UUID.randomUUID()}"
        val a=LibraryEntry("content://artwork-test/$name/a","A","audio/mpeg",album="$name A")
        val a2=a.copy(uri="content://artwork-test/$name/a2",title="A2")
        val b=a.copy(uri="content://artwork-test/$name/b",title="B",album="$name B")
        val aKey=libraryArtworkKeys(a,LibraryCollections()).last()
        val bKey=libraryArtworkKeys(b,LibraryCollections()).last()
        val bitmap=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.RED); LibraryArtwork.set(aKey,saveCroppedArtwork(context,bitmap,ArtworkCrop()))
            bitmap.eraseColor(Color.BLUE); LibraryArtwork.set(bKey,saveCroppedArtwork(context,bitmap,ArtworkCrop()))
            for(song in listOf(a,b,a2,b,a)) {
                val pixel=LibraryRepository.artwork(context,song)!!.getPixel(0,0)
                if(song.album == a.album) assertTrue(Color.red(pixel) > 200 && Color.blue(pixel) < 30)
                else assertTrue(Color.blue(pixel) > 200 && Color.red(pixel) < 30)
            }
        } finally { LibraryArtwork.set(aKey,null); LibraryArtwork.set(bKey,null); bitmap.recycle() }
    }
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
