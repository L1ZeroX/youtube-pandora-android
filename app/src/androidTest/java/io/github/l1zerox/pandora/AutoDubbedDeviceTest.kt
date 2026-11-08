package io.github.l1zerox.pandora

import android.net.Uri
import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AutoDubbedDeviceTest {
    @Test fun savedSelectionOverridesAnEarlyNegativeCacheAndSurvivesCacheRecreation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        YoutubeDL.init(context); FFmpeg.init(context)
        val file=File(context.cacheDir,"saved-selection-${System.nanoTime()}.mp4")
        val resolver=context.contentResolver
        var saved: Uri?=null
        try {
            // Intentionally no genre: exercise the saved-file receipt, not the embedded tag fallback.
            val args=listOf("-y","-f","lavfi","-i","color=c=blue:s=180x320:r=1","-t","1","-c:v","libx264","-pix_fmt","yuv420p",file.absolutePath)
            val process=NativeRuntime.command(context,"libffmpeg.so",args).start()
            val output=process.inputStream.bufferedReader().readText()
            assertEquals(output,0,process.waitFor())
            val uri=resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME,file.name)
                put(MediaStore.MediaColumns.MIME_TYPE,"video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH,saveRelativePath("MP4"))
                put(MediaStore.MediaColumns.IS_PENDING,1)
            })!!
            saved=uri
            resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            resolver.update(uri,ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null)
            val entry=LibraryEntry(uri.toString(),"fixture","video/mp4")
            val revision=SavedAudioMetadata.revision(context,entry.uri)!!
            assertFalse(LibraryMetadata.enrich(context,entry,revision).autoDubbed)
            val changes=SavedAudioMetadata.changes.value
            SavedAudioMetadata.recordAutoDubbed(context,entry.uri)
            assertTrue(SavedAudioMetadata.changes.value > changes)
            assertEquals("オートダビング版",LibraryMetadata.enrich(context,entry,revision).dubbingLabel)
            // Drop only the display cache; the persistent selected-audio receipt remains.
            context.getSharedPreferences("library_metadata",0).edit().remove(entry.uri).commit()
            val restored=LibraryMetadata.enrich(context,entry,revision)
            assertTrue(restored.autoDubbed); assertTrue(restored.short)
            assertFalse(LibraryMetadata.enrich(context,entry,"different:file").autoDubbed)
            LibraryMetadata.remove(context,entry.uri)
            assertFalse(SavedAudioMetadata.matches(context,entry.uri,revision))
        } finally {
            saved?.let { LibraryMetadata.remove(context,it.toString()); resolver.delete(it,null,null) }
            file.delete()
        }
    }
    @Test fun savedMp4MarkerSurvivesCacheReloadAndImportWhileOldCacheIsReprobed() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        YoutubeDL.init(context); FFmpeg.init(context)
        for(size in listOf("320x180","180x320")) {
            val file=File(context.cacheDir,"auto-dubbed-${System.nanoTime()}.mp4")
            val uri=Uri.fromFile(file).toString()
            val entry=LibraryEntry(uri,"fixture","video/mp4")
            try {
                val decision=audioDecision(listOf(AudioTrack("aac","ja","Japanese auto-dubbed")),emptyList())
                val args=listOf("-y","-f","lavfi","-i","color=c=blue:s=$size:r=1",
                    "-f","lavfi","-i","anullsrc=r=44100:cl=stereo","-t","1",
                    "-c:v","libx264","-pix_fmt","yuv420p","-c:a","aac") +
                    autoDubbedMetadataArgs(decision,true) + file.absolutePath
                val process=NativeRuntime.command(context,"libffmpeg.so",args).start()
                val output=process.inputStream.bufferedReader().readText()
                assertEquals(output,0,process.waitFor())
                val first=LibraryMetadata.enrich(context,entry,"fixture")
                assertEquals("オートダビング版",first.dubbingLabel)
                assertEquals(size == "180x320",first.short)
                assertTrue(LibraryMetadata.enrich(context,entry,"fixture").autoDubbed)
                val cache=context.getSharedPreferences("library_metadata",0)
                assertTrue(file.inputStream().use { mp4DubbingMarker(it.channel) })
                val missingLabel=JSONObject(cache.getString(uri,"{}")!!).put("schema",1).put("autoDubbed",false)
                cache.edit().putString(uri,missingLabel.toString()).commit()
                assertTrue(LibraryMetadata.enrich(context,entry,"fixture").autoDubbed)
                val old=JSONObject(cache.getString(uri,"{}")!!).apply { remove("autoDubbed") }
                cache.edit().putString(uri,old.toString()).commit()
                assertTrue(LibraryMetadata.enrich(context,entry,"fixture").autoDubbed)
                LibraryMetadata.remove(context,uri)
                assertTrue(LibraryMetadata.enrich(context,entry.copy(imported=true),"fixture").autoDubbed)
            } finally { LibraryMetadata.remove(context,uri); file.delete() }
        }
    }
}
