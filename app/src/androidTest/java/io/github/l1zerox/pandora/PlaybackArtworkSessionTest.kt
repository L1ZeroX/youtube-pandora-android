package io.github.l1zerox.pandora

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.test.platform.app.InstrumentationRegistry
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlin.math.abs

/** Inspect the native session metadata consumed by dashboard cards, rather than the phone UI. */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackArtworkSessionTest {
    @get:Rule val screen=createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun <T> main(block: ()->T): T {
        var result: T?=null
        instrumentation.runOnMainSync { result=block() }
        @Suppress("UNCHECKED_CAST") return result as T
    }
    private fun await(message: String, condition: ()->Boolean) {
        val end=SystemClock.elapsedRealtime()+15_000
        while(SystemClock.elapsedRealtime() < end) {
            if(condition()) return
            SystemClock.sleep(25)
        }
        fail(message)
    }
    @Test fun customCoversReachNativeSessionChangeLiveAndRestoreEmbeddedArtWithoutRestartingAudio() {
        assumeFalse(PlaybackRuntime.state.value.playing)
        val name="PandoraArtworkTest-${UUID.randomUUID()}"
        val work=File(context.cacheDir,name).apply { mkdirs() }
        val uris=mutableListOf<Uri>()
        val keys=mutableListOf<String>()
        var player: ExoPlayer?=null
        var session: MediaLibrarySession?=null
        var sync: PlaybackArtworkSync?=null
        fun bitmap(color: Int)=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        fun cover(key: String,color: Int) {
            val image=bitmap(color)
            try { LibraryArtwork.set(key,saveCroppedArtwork(context,image,ArtworkCrop())) }
            finally { image.recycle() }
        }
        try {
            YoutubeDL.init(context); FFmpeg.init(context)
            val embedded=File(work,"embedded.jpg")
            bitmap(Color.BLUE).let { image ->
                try { embedded.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.JPEG,90,it)) } }
                finally { image.recycle() }
            }
            val audio=File(work,"audio.mp3")
            val process=NativeRuntime.command(context,"libffmpeg.so",listOf("-y","-f","lavfi","-i","anullsrc=r=44100:cl=mono")+
                mp3EncodingArgs("64kbps",embedded.absolutePath)+listOf("-t","30",audio.absolutePath)).start()
            val output=process.inputStream.bufferedReader().readText()
            assertEquals(output,0,process.waitFor())
            repeat(2) { index ->
                val uri=context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME,"$index.mp3"); put(MediaStore.MediaColumns.MIME_TYPE,"audio/mpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/$name/"); put(MediaStore.MediaColumns.IS_PENDING,1)
                })!!
                uris.add(uri)
                context.contentResolver.openOutputStream(uri)!!.use { destination -> audio.inputStream().use { it.copyTo(destination) } }
                context.contentResolver.update(uri,ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null)
            }
            val entries=uris.mapIndexed { i,uri -> LibraryEntry(uri.toString(),"Song $i","audio/mpeg",durationMs=30_000,album=name,track=i+1) }
            val albumKey=albumArtworkKey(albumId(albumNameKey(name))).also(keys::add)
            val trackKey=trackArtworkKey(entries[0].uri).also(keys::add)
            cover(albumKey,Color.GREEN); cover(trackKey,Color.RED)
            lateinit var controller: MediaController
            main {
                val exo=ExoPlayer.Builder(context).build().apply { volume=0f }; player=exo
                session=MediaLibrarySession.Builder(context,exo,CarLibraryCallback(context,{entries},{LibraryCollections()}))
                    .setId(name).build()
                controller=MediaController(context,session!!.platformToken)
                sync=PlaybackArtworkSync(context,exo)
                exo.setMediaItems(entries.map { it.mediaItem() }); exo.prepare(); exo.play()
            }
            fun art()=controller.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: controller.metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            fun hasColor(color: Int): Boolean {
                val image=art() ?: return false
                val actual=image.getPixel(image.width/2,image.height/2)
                return abs(Color.red(actual)-Color.red(color)) < 20 && abs(Color.green(actual)-Color.green(color)) < 20 && abs(Color.blue(actual)-Color.blue(color)) < 20
            }
            fun waitCover(index: Int,color: Int) = await("Native session did not publish cover for item $index") {
                controller.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) == entries[index].uri && hasColor(color)
            }
            waitCover(0,Color.RED)
            assertTrue(art()!!.width <= 256 && art()!!.height <= 256)
            val firstUri=controller.metadata!!.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            assertEquals("content",Uri.parse(firstUri).scheme)
            await("Audio did not start") { main { player!!.isPlaying } }
            main {
                assertTrue(player!!.currentMediaItem!!.mediaMetadata.artworkData!!.size <= 65_536)
                player!!.seekTo(5000)
            }
            await("Seek did not complete") { main { player!!.currentPosition >= 4900 } }
            val position=main { player!!.currentPosition }; val started=SystemClock.elapsedRealtime()
            cover(trackKey,Color.YELLOW); waitCover(0,Color.YELLOW)
            assertNotEquals(firstUri,controller.metadata!!.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI))
            main {
                assertEquals(2,player!!.mediaItemCount); assertEquals(0,player!!.currentMediaItemIndex)
                assertTrue(player!!.playWhenReady)
                assertTrue(abs(player!!.currentPosition-(position+SystemClock.elapsedRealtime()-started)) < 1500)
            }
            LibraryArtwork.set(trackKey,null); waitCover(0,Color.GREEN)
            main { player!!.seekTo(1,0) }; waitCover(1,Color.GREEN)
            await("Previous queue item retained inline artwork") { main { player!!.getMediaItemAt(0).mediaMetadata.artworkData == null } }
            cover(albumKey,Color.MAGENTA); waitCover(1,Color.MAGENTA)
            LibraryArtwork.set(albumKey,null); waitCover(1,Color.BLUE)
            main {
                assertNull(player!!.currentMediaItem!!.mediaMetadata.artworkData)
                assertFalse(player!!.currentMediaItem!!.mediaMetadata.extras!!.containsKey(PLAYBACK_ARTWORK_REVISION))
            }
        } finally {
            main { sync?.close(); session?.release(); player?.release() }
            keys.forEach { LibraryArtwork.set(it,null) }
            uris.forEach { context.contentResolver.delete(it,null,null) }
            work.deleteRecursively()
        }
    }
}
