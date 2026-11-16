package io.github.l1zerox.pandora

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.SystemClock
import android.util.LruCache
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

internal interface RangePreviewLoader {
    suspend fun storyboard(video: Video): SaveRangeStoryboard?
    suspend fun frame(tile: StoryboardTile): Bitmap
    fun invalidate(video: Video) {}
}

internal class NetworkRangePreviewLoader(context: Context): RangePreviewLoader {
    private val app=context.applicationContext
    override suspend fun storyboard(video: Video): SaveRangeStoryboard? = metadataLock.withLock {
        synchronized(metadata) { metadata.get(video.url) }?.takeIf { SystemClock.elapsedRealtime()-it.first < 300_000 }?.let { return@withLock it.second }
        val id="range-preview-${UUID.randomUUID()}"
        val source=try {
            withTimeout(35_000) { runInterruptible(Dispatchers.IO) {
                YoutubeDL.init(app)
                BundledExtractor.install(app) { if(Thread.currentThread().isInterrupted) throw InterruptedException() }
                val request=YoutubeDLRequest(video.url).apply {
                    addOption("--dump-single-json"); addOption("--skip-download"); addOption("--no-playlist")
                    addOption("--ignore-no-formats-error"); addOption("--no-warnings"); addOption("--no-progress")
                    addOption("--socket-timeout","10"); addOption("--retries","1"); addOption("--extractor-retries","1")
                }
                val output=YoutubeDL.execute(request,id).out
                check(output.length <= 8*1024*1024)
                parseSaveRangeStoryboard(JSONObject(output))
            } }
        } finally {
            // This request has its own ID: never use Engine.cancel(), which also stops download descendants.
            withContext(NonCancellable+Dispatchers.IO) { YoutubeDL.destroyProcessById(id) }
        }
        currentCoroutineContext().ensureActive()
        if(source != null) synchronized(metadata) { metadata.put(video.url,SystemClock.elapsedRealtime() to source) }
        source
    }
    override fun invalidate(video: Video) {
        val old=synchronized(metadata) { metadata.remove(video.url) }
        old?.second?.sheets?.forEach { sheet -> synchronized(images) { images.remove(sheet.url) } }
    }
    override suspend fun frame(tile: StoryboardTile): Bitmap {
        val bytes=imageLock.withLock {
            synchronized(images) { images.get(tile.url) } ?: runInterruptible(Dispatchers.IO) { fetchSheet(tile.url) }
                .also { synchronized(images) { images.put(tile.url,it) } }
        }
        return runInterruptible(Dispatchers.IO) { decodeStoryboardTile(bytes,tile) }
    }
    companion object {
        private val metadataLock=Mutex()
        private val imageLock=Mutex()
        private val metadata=LruCache<String,Pair<Long,SaveRangeStoryboard>>(16)
        private val images=object: LruCache<String,ByteArray>(8*1024*1024) {
            override fun sizeOf(key: String,value: ByteArray)=value.size
        }
    }
}

private fun fetchSheet(url: String): ByteArray {
    val connection=URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout=8000; connection.readTimeout=8000
        connection.setRequestProperty("User-Agent","Mozilla/5.0")
        require(connection.responseCode == 200)
        require(connection.contentLengthLong <= 4*1024*1024)
        val deadline=SystemClock.elapsedRealtime()+15_000
        return connection.inputStream.use { input -> ByteArrayOutputStream().use { output ->
            val buffer=ByteArray(16384)
            while(true) {
                if(Thread.currentThread().isInterrupted) throw InterruptedException()
                check(SystemClock.elapsedRealtime() < deadline)
                val count=input.read(buffer); if(count < 0) break
                check(output.size()+count <= 4*1024*1024)
                output.write(buffer,0,count)
            }
            output.toByteArray()
        } }
    } finally { connection.disconnect() }
}

/** Decode just the selected cell, avoiding a full-resolution sprite bitmap for every drag event. */
@Suppress("DEPRECATION")
internal fun decodeStoryboardTile(bytes: ByteArray,tile: StoryboardTile): Bitmap {
    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
    BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong()*bounds.outHeight <= 4_000_000)
    require(tile.width in 1..640 && tile.height in 1..640 && tile.left >= 0 && tile.top >= 0 &&
        tile.left.toLong()+tile.width <= bounds.outWidth && tile.top.toLong()+tile.height <= bounds.outHeight)
    val decoder=requireNotNull(BitmapRegionDecoder.newInstance(bytes,0,bytes.size,false))
    try {
        return requireNotNull(decoder.decodeRegion(Rect(tile.left,tile.top,tile.left+tile.width,tile.top+tile.height),
            BitmapFactory.Options().apply { inPreferredConfig=Bitmap.Config.ARGB_8888 }))
    } finally { decoder.recycle() }
}
