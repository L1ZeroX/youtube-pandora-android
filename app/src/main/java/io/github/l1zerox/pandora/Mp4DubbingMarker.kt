package io.github.l1zerox.pandora

import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Read only the MP4 metadata tree. Never scan/decode video data or trust a marker in an arbitrary payload. */
internal fun mp4DubbingMarker(length: Long, read: (Long,Int)->ByteArray?): Boolean {
    var boxes=0
    fun bytes(offset: Long, count: Int): ByteArray? = if(offset < 0 || count < 0 || offset > length-count) null
        else read(offset,count)?.takeIf { it.size == count }
    fun uint(data: ByteArray, start: Int)=((data[start].toLong() and 255) shl 24) or
        ((data[start+1].toLong() and 255) shl 16) or ((data[start+2].toLong() and 255) shl 8) or (data[start+3].toLong() and 255)
    fun walk(start: Long, end: Long, parent: String): Boolean {
        var offset=start
        while(end-offset >= 8 && boxes++ < 4096) {
            val header=bytes(offset,8) ?: return false
            val type=String(header,4,4,Charsets.ISO_8859_1)
            var size=uint(header,0)
            var headerSize=8L
            if(size == 1L) {
                val extended=bytes(offset+8,8) ?: return false
                val high=uint(extended,0)
                if(high > Int.MAX_VALUE) return false
                size=(high shl 32) or uint(extended,4); headerSize=16
            } else if(size == 0L) size=end-offset
            if(size < headerSize || size > end-offset) return false
            val body=offset+headerSize
            val limit=offset+size
            val descend=when(parent) {
                "root" -> type == "moov"
                "moov" -> type in listOf("udta","meta")
                "udta" -> type == "meta"
                "meta" -> type == "ilst"
                "ilst" -> type == "©gen"
                else -> false
            }
            if(descend) {
                val childStart=body+if(type == "meta") 4 else 0 // FullBox version/flags
                if(childStart <= limit && walk(childStart,limit,type)) return true
            } else if(parent == "©gen" && type == "data" && limit-body in 9..512) {
                val value=bytes(body,(limit-body).toInt()) ?: return false
                val encoding=uint(value,0)
                val text=when(encoding) {
                    1L -> String(value,8,value.size-8,Charsets.UTF_8)
                    2L -> String(value,8,value.size-8,Charsets.UTF_16BE)
                    else -> ""
                }
                if(isAutoDubbedMetadata(text)) return true
            }
            offset=limit
        }
        return false
    }
    return length >= 8 && runCatching { walk(0,length,"root") }.getOrDefault(false)
}

internal fun mp4DubbingMarker(channel: FileChannel, start: Long = 0, declaredLength: Long = -1): Boolean = runCatching {
    val available=(channel.size()-start).coerceAtLeast(0)
    val length=if(declaredLength >= 0) minOf(available,declaredLength) else available
    mp4DubbingMarker(length) { offset,count ->
        val buffer=ByteBuffer.allocate(count)
        while(buffer.hasRemaining()) {
            if(channel.read(buffer,start+offset+buffer.position()) <= 0) return@mp4DubbingMarker null
        }
        buffer.array()
    }
}.getOrDefault(false)
