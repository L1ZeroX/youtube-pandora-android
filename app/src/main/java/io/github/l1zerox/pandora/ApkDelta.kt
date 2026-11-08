package io.github.l1zerox.pandora

import java.io.DataInputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

internal const val MAX_DELTA_APK_BYTES=1024L*1024*1024
internal fun sha256File(file: File): ByteArray {
    val hash=MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer=ByteArray(128*1024)
        while(true) { val n=input.read(buffer); if(n < 0) break; hash.update(buffer,0,n) }
    }
    return hash.digest()
}
internal fun ByteArray.hexDigest()=joinToString("") { "%02x".format(it) }

/** Reconstruct the exact signed APK bytes. No ZIP re-encoding and no installation of partial code. */
internal fun applyApkDelta(base: File, patch: File, output: File, targetSize: Long, targetDigest: String,
    onProgress: (Long)->Unit = {}) {
    require(base.canonicalFile != output.canonicalFile && patch.canonicalFile != output.canonicalFile)
    require(targetSize in 1..MAX_DELTA_APK_BYTES && Regex("sha256:[a-fA-F0-9]{64}").matches(targetDigest))
    try {
        DataInputStream(GZIPInputStream(patch.inputStream().buffered())).use { input ->
            val magic=ByteArray(8).also(input::readFully)
            require(magic.contentEquals("PNDRDLT1".toByteArray(Charsets.US_ASCII))) { "差分の形式が一致しません。" }
            val baseSize=input.readLong(); val declaredSize=input.readLong()
            val baseHash=ByteArray(32).also(input::readFully); val targetHash=ByteArray(32).also(input::readFully)
            val count=input.readInt()
            require(baseSize in 1..MAX_DELTA_APK_BYTES && declaredSize == targetSize && count in 1..100_000)
            require(base.isFile && base.length() == baseSize && sha256File(base).contentEquals(baseHash)) { "元のAPKが一致しません。" }
            require(targetHash.hexDigest().equals(targetDigest.substringAfter(':'),true)) { "更新先のAPKが一致しません。" }
            val hash=MessageDigest.getInstance("SHA-256")
            var written=0L
            RandomAccessFile(base,"r").use { source -> output.outputStream().buffered().use { destination ->
                val buffer=ByteArray(128*1024)
                repeat(count) {
                    val kind=input.readUnsignedByte()
                    require(kind == 1 || kind == 2) { "差分の命令が不正です。" }
                    val offset=if(kind == 1) input.readLong() else 0L
                    var remaining=input.readLong()
                    require(remaining > 0 && remaining <= targetSize-written)
                    if(kind == 1) {
                        require(offset >= 0 && remaining <= baseSize && offset <= baseSize-remaining)
                        source.seek(offset)
                    }
                    while(remaining > 0) {
                        val length=minOf(remaining,buffer.size.toLong()).toInt()
                        if(kind == 1) source.readFully(buffer,0,length) else input.readFully(buffer,0,length)
                        destination.write(buffer,0,length); hash.update(buffer,0,length)
                        remaining-=length; written+=length; onProgress(written)
                    }
                }
            } }
            require(written == targetSize && input.read() == -1) { "差分が途中で終わったか、余分なデータがあります。" }
            require(hash.digest().contentEquals(targetHash)) { "復元したAPKの検証に失敗しました。" }
        }
    } catch(error: Exception) { output.delete(); throw error }
}
