package io.github.l1zerox.pandora

import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.Executors

class SaveRangeRuntimeTest {
    @Test fun downloadsClipsThroughEngineAndPublishesThemWithRangeNames() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val original=Store.state.value
        val details=mutableListOf<String>()
        val engine=Engine(context) { _,_,detail -> if(detail.startsWith("PANDORA_CONVERT|")) details+=detail }
        engine.init()
        val work=File(context.cacheDir,"save-range-runtime-test").apply { mkdirs() }
        val saved=mutableListOf<Uri>()
        val cover=File(File(context.filesDir,"artwork").apply { mkdirs() },"save-range-test.jpg")
        val server=ServerSocket(0)
        val executor=Executors.newSingleThreadExecutor()
        fun native(binary: String,args: List<String>): String {
            val process=NativeRuntime.command(context,binary,args).start()
            val text=process.inputStream.bufferedReader().readText()
            assertEquals(text,0,process.waitFor())
            return text
        }
        try {
            val fixture=File(work,"source.mp4")
            native("libffmpeg.so",listOf("-y","-f","lavfi","-i","color=c=blue:s=160x96:r=16:d=4",
                "-f","lavfi","-i","sine=frequency=880:sample_rate=48000:duration=4","-c:v","libx264",
                "-pix_fmt","yuv420p","-g","240","-c:a","aac","-movflags","+faststart",fixture.absolutePath))
            val bitmap=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.CYAN)
            try { cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,90,it) } } finally { bitmap.recycle() }
            executor.submit {
                while(!server.isClosed) runCatching {
                    server.accept().use { socket ->
                        val reader=socket.getInputStream().bufferedReader()
                        val request=reader.readLine().orEmpty()
                        while(!reader.readLine().isNullOrEmpty()) { }
                        val bytes=fixture.readBytes()
                        val output=socket.getOutputStream()
                        output.write("HTTP/1.1 200 OK\r\nContent-Type: video/mp4\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        if(!request.startsWith("HEAD")) output.write(bytes)
                        output.flush()
                    }
                }
            }
            val video=Video("range-test","http://127.0.0.1:${server.localPort}/source.mp4","Save range test",duration=4.0)
            val range=SaveRange(true,1,3)
            val settings=listOf(Preferences(saveRange=range),
                Preferences(format="MP3",saveRange=range,artworkMode="ギャラリー",artworkImage=cover.absolutePath),
                Preferences(saveRange=range.copy(enabled=false)))
            for(p in settings) {
                Store.state.value=ScreenState(videos=listOf(video),preferences=p)
                Store.save(); Store.init(context)
                assertEquals(p,Store.state.value.preferences)
                details.clear()
                val uri=Uri.parse(engine.download(video,p)); saved+=uri
                context.contentResolver.query(uri,arrayOf(MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.IS_PENDING),null,null,null)!!.use {
                    assertTrue(it.moveToFirst()); assertEquals(0,it.getInt(1))
                    assertEquals(p.saveRange.enabled,it.getString(0).contains("[clip_0-01-0-03]"))
                }
                val copy=File(work,"verified.${p.format.lowercase()}")
                context.contentResolver.openInputStream(uri)!!.use { input -> copy.outputStream().use { input.copyTo(it) } }
                val json=JSONObject(native("libffprobe.so",listOf("-v","error","-show_streams","-show_format","-of","json",copy.absolutePath)))
                val expected=if(p.saveRange.enabled) 2.0 else 4.0
                assertEquals(expected,json.getJSONObject("format").getDouble("duration"),.15)
                assertTrue(details.any { it.split('|')[2].toDouble() == expected })
                if(p.format == "MP3") {
                    val streams=json.getJSONArray("streams")
                    assertTrue((0 until streams.length()).any { streams.getJSONObject(it).optJSONObject("disposition")?.optInt("attached_pic") == 1 })
                }
            }
            assertThrows(IllegalArgumentException::class.java) { engine.download(video,Preferences(saveRange=SaveRange(true,4))) }
        } finally {
            Store.state.value=original; Store.save()
            saved.forEach { context.contentResolver.delete(it,null,null) }
            server.close(); executor.shutdownNow(); cover.delete(); work.deleteRecursively()
        }
    }
}
