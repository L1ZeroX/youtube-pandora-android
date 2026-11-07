package io.github.l1zerox.pandora

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

class PauseResumeTest {
    @Test fun pauseKeepsPartialFileAndResumeUsesHttpRangeThenPublishesOnce() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val initialized=Engine(context) { _,_,_ -> }; initialized.init()
        val fixture=File(context.cacheDir,"pause-resume-fixture.mp4")
        val generate=NativeRuntime.command(context,"libffmpeg.so",listOf("-y","-f","lavfi","-i",
            "testsrc2=size=640x360:rate=30","-t","12","-c:v","libx264","-preset","ultrafast","-crf","18","-pix_fmt","yuv420p",fixture.absolutePath)).start()
        val output=generate.inputStream.bufferedReader().readText()
        assertEquals(output,0,generate.waitFor())
        val server=ServerSocket(0)
        val serving=Executors.newCachedThreadPool()
        val worker=Executors.newSingleThreadExecutor()
        val resumedRange=AtomicBoolean(false)
        val progress=CountDownLatch(1)
        serving.submit {
            while(!server.isClosed) runCatching {
                val socket=server.accept()
                serving.submit {
                    runCatching {
                        socket.use {
                            val input=it.getInputStream().bufferedReader()
                            val first=input.readLine().orEmpty()
                            var offset=0L
                            while(true) {
                                val header=input.readLine(); if(header.isNullOrEmpty()) break
                                if(header.startsWith("Range:",true)) offset=header.substringAfter("bytes=").substringBefore('-').toLongOrNull() ?: 0L
                            }
                            if(offset > 0) resumedRange.set(true)
                            val total=fixture.length(); val out=it.getOutputStream()
                            out.write(("HTTP/1.1 ${if(offset > 0) "206 Partial Content" else "200 OK"}\r\nContent-Type: video/mp4\r\nAccept-Ranges: bytes\r\nContent-Length: ${total-offset}\r\n"+
                                (if(offset > 0) "Content-Range: bytes $offset-${total-1}/$total\r\n" else "")+"Connection: close\r\n\r\n").toByteArray())
                            if(!first.startsWith("HEAD")) fixture.inputStream().use { file ->
                                file.skip(offset)
                                val buffer=ByteArray(8192)
                                while(true) { val count=file.read(buffer); if(count < 0) break; out.write(buffer,0,count); out.flush(); Thread.sleep(30) }
                            }
                        }
                    }
                }
            }
        }
        val video=Video("pause-test","http://127.0.0.1:${server.localPort}/fixture.mp4","Pause resume test")
        val work=transferWorkspace(context.cacheDir,video,Preferences())
        val saved=mutableListOf<Uri>()
        val firstEngine=Engine(context) { _,_,detail ->
            if(transferProgress(detail)?.fraction?.let { it > .02f && it < .9f } == true) progress.countDown()
        }
        try {
            firstEngine.init()
            val first=worker.submit<String> { firstEngine.download(video,Preferences(),work) }
            assertTrue("Expected measurable download progress",progress.await(45,TimeUnit.SECONDS))
            firstEngine.pause()
            try { saved+=Uri.parse(first.get(30,TimeUnit.SECONDS)); fail("Download must be paused") } catch(_: ExecutionException) { }
            assertTrue(firstEngine.paused)
            assertTrue("Partial media must survive pause",work.walkTopDown().any { it.name.endsWith(".part") && it.length() > 0 })
            val secondEngine=Engine(context) { _,_,_ -> }; secondEngine.init()
            saved+=Uri.parse(secondEngine.download(video,Preferences(),work))
            assertTrue("Resume must request the remaining bytes",resumedRange.get())
            assertEquals(1,saved.size)
            assertFalse("Completed workspace is cleaned",work.exists())
        } finally {
            firstEngine.cancel(); server.close(); serving.shutdownNow(); worker.shutdownNow()
            saved.forEach { context.contentResolver.delete(it,null,null) }
            work.deleteRecursively(); fixture.delete()
        }
    }
}
