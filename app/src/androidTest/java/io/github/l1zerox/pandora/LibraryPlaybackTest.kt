package io.github.l1zerox.pandora

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

@androidx.annotation.OptIn(UnstableApi::class)
class LibraryPlaybackTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun insert(format: String, pending: Boolean = false, file: File? = null): Uri {
        val resolver=context.contentResolver
        val uri=resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,"Pandora-library-test-${System.nanoTime()}.${format.lowercase()}")
            put(MediaStore.MediaColumns.MIME_TYPE,if(format == "MP4") "video/mp4" else "audio/mpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH,saveRelativePath(format))
            put(MediaStore.MediaColumns.IS_PENDING,1)
        })!!
        if(file != null) resolver.openOutputStream(uri)!!.use { output -> file.inputStream().use { it.copyTo(output) } }
        if(!pending) resolver.update(uri,ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null)
        return uri
    }

    @Test fun savedLibrarySurvivesQueueRemovalAndExcludesPendingOrDeletedFiles() = runBlocking {
        val original=Store.state.value
        val saved=insert("MP4")
        val pending=insert("MP3",true)
        try {
            Store.state.value=ScreenState()
            val entries=LibraryRepository.load(context)
            assertTrue(entries.any { it.uri == saved.toString() && it.video })
            assertFalse(entries.any { it.uri == pending.toString() })
            context.contentResolver.delete(saved,null,null)
            assertFalse(LibraryRepository.load(context).any { it.uri == saved.toString() })
        } finally {
            Store.state.value=original
            context.contentResolver.delete(saved,null,null)
            context.contentResolver.delete(pending,null,null)
        }
    }

    @Test fun libraryComponentsAndPipAreEnabledInBothChannels() {
        val flags=PackageManager.MATCH_DISABLED_COMPONENTS
        val library=context.packageManager.getActivityInfo(ComponentName(context,LibraryActivity::class.java),flags)
        val player=context.packageManager.getActivityInfo(ComponentName(context,PlayerActivity::class.java),flags)
        val service=context.packageManager.getServiceInfo(ComponentName(context,PlaybackService::class.java),flags)
        assertTrue(library.enabled)
        assertTrue(player.enabled)
        assertTrue(service.enabled)
        // FLAG_SUPPORTS_PICTURE_IN_PICTURE = 0x400000 (ActivityInfo constant is hidden on some SDKs).
        assertTrue(player.flags and 0x400000 != 0)
        assertFalse(player.exported); assertTrue(service.exported)
    }

    @Test fun portraitDownloadIsClassifiedAndDeletionRemovesOnlyItsFileAndRegistration() = runBlocking {
        YoutubeDL.init(context); FFmpeg.init(context)
        val file=File(context.cacheDir,"library-portrait-${System.nanoTime()}.mp4")
        val process=NativeRuntime.command(context,"libffmpeg.so",listOf("-y","-f","lavfi","-i","color=c=blue:s=180x320:r=1",
            "-t","1","-c:v","libx264","-pix_fmt","yuv420p",file.absolutePath)).start()
        val output=process.inputStream.bufferedReader().readText()
        assertEquals(output,0,process.waitFor())
        val portrait=insert("MP4",file=file)
        val other=insert("MP3")
        val prefs=context.getSharedPreferences("library",0)
        val original=prefs.getString("items","[]")
        try {
            // The same URI can also have been imported. Deletion must not resurrect that duplicate.
            prefs.edit().putString("items",JSONArray().put(JSONObject().put("uri",portrait.toString())
                .put("title","portrait").put("mime","video/mp4")).toString()).commit()
            val entry=LibraryRepository.load(context).single { it.uri == portrait.toString() }
            assertEquals(180,entry.width); assertEquals(320,entry.height)
            assertTrue(entry.short); assertEquals("ショート",entry.category)
            assertTrue(LibraryRepository.load(context).single { it.uri == portrait.toString() }.short)
            assertEquals(LibraryDeleteResult.Deleted,LibraryDeletion.delete(context,entry))
            val remaining=LibraryRepository.load(context)
            assertFalse(remaining.any { it.uri == portrait.toString() })
            assertTrue(remaining.any { it.uri == other.toString() })
            assertFalse(context.getSharedPreferences("library_metadata",0).contains(portrait.toString()))
            context.contentResolver.query(portrait,arrayOf(MediaStore.MediaColumns._ID),null,null,null)!!.use { assertEquals(0,it.count) }
        } finally {
            prefs.edit().putString("items",original).commit()
            context.contentResolver.delete(portrait,null,null); context.contentResolver.delete(other,null,null); file.delete()
        }
    }

    @Test fun removingAnImportKeepsItsFileAndFailedDeletionKeepsItsRegistration() = runBlocking {
        val uri=insert("MP3")
        val entry=LibraryEntry(uri.toString(),"fixture","audio/mpeg",imported=true)
        val prefs=context.getSharedPreferences("library",0)
        val original=prefs.getString("items","[]")
        val registered=JSONArray().put(JSONObject().put("uri",uri.toString()).put("title","fixture").put("mime","audio/mpeg")).toString()
        try {
            prefs.edit().putString("items",registered).commit()
            LibraryDeletion.removeImport(context,entry)
            assertEquals(0,JSONArray(prefs.getString("items","[]")).length())
            context.contentResolver.query(uri,arrayOf(MediaStore.MediaColumns._ID),null,null,null)!!.use { assertEquals(1,it.count) }
            context.contentResolver.delete(uri,null,null)
            prefs.edit().putString("items",registered).commit()
            assertTrue(runCatching { LibraryDeletion.delete(context,entry) }.isFailure)
            assertEquals(registered,prefs.getString("items","[]"))
        } finally {
            prefs.edit().putString("items",original).commit()
            context.contentResolver.delete(uri,null,null)
        }
    }

    @Test fun musicPlaybackSurvivesClosingItsScreenAndRespondsToPauseResumeStop() {
        YoutubeDL.init(context); FFmpeg.init(context)
        val file=File(context.cacheDir,"library-silence.mp3")
        val process=NativeRuntime.command(context,"libffmpeg.so",listOf("-y","-f","lavfi","-i","anullsrc=r=44100:cl=stereo","-t","30","-c:a","libmp3lame",file.absolutePath)).start()
        val output=process.inputStream.bufferedReader().readText()
        assertEquals(output,0,process.waitFor())
        val uri=insert("MP3",file=file)
        val library=LibraryPlayback(context)
        var future: ListenableFuture<MediaController>? = null
        val scenario=ActivityScenario.launch<PlayerActivity>(Intent(context,PlayerActivity::class.java).putExtra("uri",uri.toString()))
        try {
            instrumentation.runOnMainSync {
                future=MediaController.Builder(context,SessionToken(context,ComponentName(context,PlaybackService::class.java))).buildAsync()
            }
            val controller=future!!.get(10,TimeUnit.SECONDS)
            waitFor { controller.currentMediaItem?.mediaId == uri.toString() && controller.playbackState == Player.STATE_READY && controller.currentPosition > 250 }
            val music=MusicPlayback(controller)
            instrumentation.runOnMainSync { music.refresh(queueChanged=true); music.toggle() }
            waitFor { !controller.playWhenReady }
            instrumentation.runOnMainSync { music.seek(uri.toString(),.25f) }
            waitFor { controller.currentPosition in 7000..8000 }
            instrumentation.runOnMainSync { music.shuffle(); music.repeat() }
            waitFor { controller.shuffleModeEnabled && controller.repeatMode == Player.REPEAT_MODE_ALL }
            instrumentation.runOnMainSync { music.shuffle(); music.repeat(); music.repeat(); music.toggle() }
            waitFor { controller.isPlaying && !controller.shuffleModeEnabled && controller.repeatMode == Player.REPEAT_MODE_OFF }
            scenario.close()
            waitFor { controller.isPlaying && controller.currentPosition > 750 }
            instrumentation.runOnMainSync { library.connect() }
            waitFor { library.controller != null && library.seekable && library.uri == uri.toString() }
            instrumentation.runOnMainSync { library.toggle(uri.toString()) }
            waitFor { !controller.playWhenReady && !library.playing }
            instrumentation.runOnMainSync { library.seek(uri.toString(),.5f) }
            waitFor { controller.currentPosition in 14000..16000 && library.position in 14000..16000 }
            instrumentation.runOnMainSync { library.seek("stale-track",0f) }
            waitFor { controller.currentPosition in 14000..16000 }
            instrumentation.runOnMainSync { library.toggle(uri.toString()) }
            waitFor { controller.isPlaying }
            instrumentation.runOnMainSync { library.disconnect() }
            waitFor { controller.isPlaying }
            instrumentation.runOnMainSync { library.connect() }
            waitFor { library.controller != null && library.playing && library.position > 14000 }
            instrumentation.runOnMainSync { controller.stop(); controller.clearMediaItems() }
            waitFor { controller.playbackState == Player.STATE_IDLE && controller.mediaItemCount == 0 }
        } finally {
            scenario.close()
            instrumentation.runOnMainSync { library.disconnect(); future?.let { MediaController.releaseFuture(it) } }
            context.stopService(Intent(context,PlaybackService::class.java))
            context.contentResolver.delete(uri,null,null); file.delete()
        }
    }

    @Test fun videoContinuesInPipAndPausesWhenItsWindowCloses() {
        assumeTrue(context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))
        YoutubeDL.init(context); FFmpeg.init(context)
        val file=File(context.cacheDir,"library-pip.mp4")
        val process=NativeRuntime.command(context,"libffmpeg.so",listOf("-y","-f","lavfi","-i","color=c=blue:s=320x180:r=24",
            "-t","30","-c:v","libx264","-pix_fmt","yuv420p",file.absolutePath)).start()
        val output=process.inputStream.bufferedReader().readText()
        assertEquals(output,0,process.waitFor())
        val uri=insert("MP4",file=file)
        var future: ListenableFuture<MediaController>? = null
        val scenario=ActivityScenario.launch<PlayerActivity>(Intent(context,PlayerActivity::class.java).putExtra("uri",uri.toString()))
        try {
            instrumentation.runOnMainSync {
                future=MediaController.Builder(context,SessionToken(context,ComponentName(context,PlaybackService::class.java))).buildAsync()
            }
            val controller=future!!.get(10,TimeUnit.SECONDS)
            waitFor { controller.currentMediaItem?.mediaId == uri.toString() && controller.isPlaying && controller.videoSize.width > 0 }
            var activity: PlayerActivity? = null
            scenario.onActivity {
                activity=it
                it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            waitFor { activity?.resources?.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE && activity?.videoFullscreen == true && controller.isPlaying }
            waitFor { activity?.let { ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.statusBars()) == false } == true }
            scenario.onActivity { it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            waitFor { activity?.resources?.configuration?.orientation == Configuration.ORIENTATION_PORTRAIT && activity?.videoFullscreen == false && controller.isPlaying }
            scenario.onActivity {
                assertTrue(it.enterPictureInPictureMode(PictureInPictureParams.Builder().build()))
            }
            waitFor { activity?.isInPictureInPictureMode == true && controller.isPlaying }
            scenario.close()
            waitFor { !controller.playWhenReady }
        } finally {
            scenario.close()
            instrumentation.runOnMainSync { future?.let { MediaController.releaseFuture(it) } }
            context.stopService(Intent(context,PlaybackService::class.java))
            context.contentResolver.delete(uri,null,null); file.delete()
        }
    }

    private fun waitFor(condition: ()->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+15000
        while(SystemClock.elapsedRealtime() < deadline) {
            var success=false
            instrumentation.runOnMainSync { success=condition() }
            if(success) return
            SystemClock.sleep(100)
        }
        fail("Playback state did not arrive in time")
    }
}
