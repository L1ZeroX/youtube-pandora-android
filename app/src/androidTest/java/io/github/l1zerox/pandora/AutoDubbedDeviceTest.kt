package io.github.l1zerox.pandora

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AutoDubbedDeviceTest {
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
