package io.github.l1zerox.pandora

import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The platform browser uses the same legacy service path used by Android Auto. */
class CarLegacyBrowseTest {
    @get:Rule val screen=createAndroidComposeRule<ComponentActivity>()
    @Test fun legacyBrowserOpensAlbumsSelectsASongAndKeepsTheAlbumQueue() {
        assumeFalse(PlaybackRuntime.state.value.playing)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val folder="PandoraCarTest-${UUID.randomUUID()}"
        val uris=mutableListOf<Uri>()
        var browser: MediaBrowser?=null
        try {
            repeat(2) { index ->
                val uri=context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME,"$index.wav"); put(MediaStore.MediaColumns.MIME_TYPE,"audio/wav")
                    put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/$folder/"); put(MediaStore.MediaColumns.IS_PENDING,1)
                })!!
                uris.add(uri)
                val bytes=160_000
                val header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).put("RIFF".toByteArray()).putInt(bytes+36)
                    .put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
                    .put("data".toByteArray()).putInt(bytes).array()
                context.contentResolver.openOutputStream(uri)!!.use { it.write(header); it.write(ByteArray(bytes)) }
                context.contentResolver.update(uri,ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null)
            }
            CarBrowseFixtureService.entries=uris.mapIndexed { i,uri -> LibraryEntry(uri.toString(),"Song $i","audio/wav",durationMs=10_000,album=folder,track=i+1) }
            val connected=CountDownLatch(1); var failed=false
            instrumentation.runOnMainSync {
                browser=MediaBrowser(context,ComponentName(context,CarBrowseFixtureService::class.java),object: MediaBrowser.ConnectionCallback() {
                    override fun onConnected() { connected.countDown() }
                    override fun onConnectionFailed() { failed=true; connected.countDown() }
                },null).also { it.connect() }
            }
            assertTrue("Legacy browser connection timed out",connected.await(10,TimeUnit.SECONDS)); assertFalse(failed)
            fun children(parent: String): List<MediaBrowser.MediaItem> {
                val done=CountDownLatch(1); var result: List<MediaBrowser.MediaItem>?=null
                instrumentation.runOnMainSync {
                    browser!!.subscribe(parent,object: MediaBrowser.SubscriptionCallback() {
                        override fun onChildrenLoaded(parentId: String, list: MutableList<MediaBrowser.MediaItem>) { result=list; done.countDown() }
                        override fun onError(parentId: String) { done.countDown() }
                    })
                }
                assertTrue("Legacy children timed out",done.await(10,TimeUnit.SECONDS))
                return requireNotNull(result)
            }
            assertEquals(CAR_ALBUMS,children(browser!!.root).first().mediaId)
            val album=children(CAR_ALBUMS).single()
            assertTrue(album.isBrowsable); assertEquals(folder,album.description.title.toString())
            val songs=children(album.mediaId!!)
            assertEquals(2,songs.size); assertTrue(songs.all { it.isPlayable })
            lateinit var controller: MediaController
            instrumentation.runOnMainSync {
                controller=MediaController(context,browser!!.sessionToken)
                controller.transportControls.playFromMediaId(songs[1].mediaId,null)
            }
            fun waitFor(uri: Uri) {
                val end=SystemClock.elapsedRealtime()+10_000
                while(SystemClock.elapsedRealtime() < end) {
                    if(controller.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) == uri.toString()) return
                    SystemClock.sleep(20)
                }
                fail("Legacy selection did not reach the chosen music item")
            }
            waitFor(uris[1])
            assertEquals(2,controller.queue!!.size)
            instrumentation.runOnMainSync { controller.transportControls.skipToPrevious() }; waitFor(uris[0])
            instrumentation.runOnMainSync { controller.transportControls.skipToNext() }; waitFor(uris[1])
            instrumentation.runOnMainSync { controller.transportControls.stop() }
        } finally {
            instrumentation.runOnMainSync { browser?.disconnect() }
            context.stopService(Intent(context,CarBrowseFixtureService::class.java))
            CarBrowseFixtureService.entries=emptyList()
            uris.forEach { context.contentResolver.delete(it,null,null) }
        }
    }
}
