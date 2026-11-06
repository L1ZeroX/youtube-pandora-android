package io.github.l1zerox.pandora

import android.net.Uri
import android.content.ContentUris
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class RuntimeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun native(binary: String, args: List<String>): String {
        val builder = NativeRuntime.command(context,binary,args)
        val process = builder.start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output,0,process.waitFor())
        return output
    }
    @Test fun downloadsAndConvertsSyntheticMedia() {
        YoutubeDL.init(context); FFmpeg.init(context)
        val fixture = File(context.cacheDir,"fixture.mp4")
        native("libffmpeg.so",listOf("-y","-f","lavfi","-i","color=c=blue:s=320x180:r=30","-f","lavfi","-i","sine=frequency=440:sample_rate=44100","-t","2","-c:v","libx264","-pix_fmt","yuv420p","-c:a","aac","-movflags","+faststart",fixture.absolutePath))
        val server = ServerSocket(0)
        val executor = Executors.newSingleThreadExecutor()
        executor.submit {
            while(!server.isClosed) runCatching {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    val request = reader.readLine().orEmpty()
                    while(!reader.readLine().isNullOrEmpty()) { }
                    val bytes = fixture.readBytes()
                    val out = socket.getOutputStream()
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: video/mp4\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    if(!request.startsWith("HEAD")) out.write(bytes)
                    out.flush()
                }
            }
        }
        val saved = mutableListOf<Uri>()
        try {
            val url = "http://127.0.0.1:${server.localPort}/fixture.mp4"
            val engine = Engine(context) { _,_,_ -> }
            engine.init()
            val video = engine.discover(url,false).single()
            assertTrue(video.title.isNotBlank())
            for(p in listOf(Preferences(),Preferences(fps="24fps"),Preferences(format="MP3"))) {
                val uri = Uri.parse(engine.download(video,p)); saved += uri
                // Check the media is published to the collection used by gallery/music apps.
                assertEquals("downloads",uri.pathSegments[1])
                context.contentResolver.query(uri,arrayOf(MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.IS_PENDING,MediaStore.MediaColumns.MIME_TYPE),null,null,null)!!.use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Download/YoutubePandora!/${p.format.lowercase()}/",cursor.getString(0))
                    assertEquals(0,cursor.getInt(1))
                    assertEquals(if(p.format == "MP4") "video/mp4" else "audio/mpeg",cursor.getString(2))
                }
                val mediaCollection = if(p.format == "MP4") MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val mediaUri = ContentUris.withAppendedId(mediaCollection,ContentUris.parseId(uri))
                var indexed = false
                for(attempt in 0 until 50) {
                    indexed = context.contentResolver.query(mediaUri,arrayOf(MediaStore.MediaColumns._ID),null,null,null)?.use { it.moveToFirst() } == true
                    if(indexed) break
                    Thread.sleep(100)
                }
                assertTrue("Saved media must be visible through the gallery/music collection",indexed)
                val copy = File(context.cacheDir,"verify.${p.format.lowercase()}")
                context.contentResolver.openInputStream(uri)!!.use { input -> copy.outputStream().use { input.copyTo(it) } }
                val json = JSONObject(native("libffprobe.so",listOf("-v","error","-show_streams","-show_format","-of","json",copy.absolutePath)))
                val streams = json.getJSONArray("streams")
                val codecs = (0 until streams.length()).map { streams.getJSONObject(it).getString("codec_name") }
                if(p.format == "MP3") assertTrue(codecs.contains("mp3")) else {
                    assertTrue(codecs.containsAll(listOf("h264","aac")))
                    if(p.fps == "24fps") assertEquals("24/1",streams.getJSONObject(0).getString("r_frame_rate"))
                }
                assertTrue(json.getJSONObject("format").getString("duration").toDouble() in 1.8..2.4)
                copy.delete()
            }
        } finally {
            saved.forEach { context.contentResolver.delete(it,null,null) }
            server.close(); executor.shutdownNow(); fixture.delete()
        }
    }
}
