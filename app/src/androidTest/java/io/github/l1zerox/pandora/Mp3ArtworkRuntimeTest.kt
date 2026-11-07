package io.github.l1zerox.pandora

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Mp3ArtworkRuntimeTest {
    @Test fun galleryImportAndMp3ContainOneFrontCoverAndPlayableAudio() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        Engine(context) { _,_,_ -> }.init()
        val work=File(context.cacheDir,"artwork-test").apply { mkdirs() }
        var imported: File? = null
        val original=Store.state.value
        fun native(binary: String,args: List<String>): String {
            val process=NativeRuntime.command(context,binary,args).start()
            val text=process.inputStream.bufferedReader().readText()
            assertEquals(text,0,process.waitFor())
            return text
        }
        try {
            val png=File(work,"original.png")
            val bitmap=Bitmap.createBitmap(2048,1024,Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.CYAN)
            png.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
            val preview=loadArtworkBitmap(context,Uri.fromFile(png))
            try { imported=saveCroppedArtwork(context,preview,ArtworkCrop()) } finally { preview.recycle() }
            val p=Preferences(format="MP3",artworkMode="ギャラリー",artworkImage=imported.absolutePath)
            Store.preferences(p); Store.init(context)
            assertEquals(p,Store.state.value.preferences)
            val cover=prepareMp3Artwork(context,Video("a","https://example.com","A"),p,work)!!
            val audio=File(work,"audio.wav")
            native("libffmpeg.so",listOf("-y","-f","lavfi","-i","sine=frequency=440:duration=2",audio.absolutePath))
            val output=File(work,"output.mp3")
            native("libffmpeg.so",conversionInputArgs(audio.absolutePath)+mp3EncodingArgs(p.audio,cover.absolutePath)+output.absolutePath)
            val result=JSONObject(native("libffprobe.so",listOf("-v","error","-show_streams","-show_format","-of","json",output.absolutePath)))
            val streams=result.getJSONArray("streams")
            val list=(0 until streams.length()).map { streams.getJSONObject(it) }
            assertEquals(1,list.count { it.optString("codec_name") == "mp3" })
            val art=list.single { it.optString("codec_type") == "video" }
            assertEquals("mjpeg",art.getString("codec_name"))
            assertEquals(1,art.getJSONObject("disposition").getInt("attached_pic"))
            assertEquals("Cover (front)",art.getJSONObject("tags").getString("comment"))
            assertEquals(1024,art.getInt("width")); assertEquals(1024,art.getInt("height"))
            assertTrue(result.getJSONObject("format").getString("duration").toDouble() in 1.9..2.2)
        } finally {
            Store.state.value=original; Store.save()
            imported?.delete(); work.deleteRecursively()
        }
    }
}
