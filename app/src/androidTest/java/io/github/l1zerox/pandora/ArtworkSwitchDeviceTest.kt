package io.github.l1zerox.pandora

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ArtworkSwitchDeviceTest {
    @get:Rule val screen=createComposeRule()

    @Test fun newSongClearsThePreviousImageAndLateResultsCannotReplaceTheCurrentCover() {
        val a=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
        val b=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
        val pending=List(3) { CompletableDeferred<Bitmap?>() }
        var request by mutableIntStateOf(0)
        lateinit var shown: State<Bitmap?>
        screen.setContent {
            val index=request
            shown=rememberArtworkResult(index) { withContext(NonCancellable) { pending[index].await() } }
        }
        screen.runOnIdle { pending[0].complete(a) }
        screen.waitUntil { shown.value === a }
        screen.runOnIdle { request=1 }
        screen.runOnIdle { assertNull(shown.value); request=2 }
        screen.runOnIdle { pending[2].complete(b) }
        screen.waitUntil { shown.value === b }
        screen.runOnIdle { pending[1].complete(a) }
        screen.waitForIdle()
        screen.runOnIdle { assertSame(b,shown.value) }
    }

    @Test fun artworkLookupUsesTheCurrentItemsAlbumAndCanResolveMissingTagsFromThatUri() {
        val tagged=MediaItem.Builder().setMediaId("content://song/a")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("A").setAlbumTitle("Album A").build()).build()
        val unknown=MediaItem.Builder().setMediaId("content://song/b")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("B").build()).build()
        assertEquals("Album A",tagged.artworkEntry().album)
        assertEquals("content://song/b",unknown.artworkEntry().uri)
        assertEquals("",unknown.artworkEntry().album)
    }
}
