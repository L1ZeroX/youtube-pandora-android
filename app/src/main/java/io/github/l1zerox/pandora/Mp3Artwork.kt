package io.github.l1zerox.pandora

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import kotlin.math.max

internal const val OPEN_ARTWORK = "io.github.l1zerox.pandora.OPEN_ARTWORK"

internal fun artworkSource(video: Video, p: Preferences): String? = when {
    p.format != "MP3" || p.artworkMode == "なし" -> null
    p.artworkMode == "ギャラリー" -> p.artworkImage.ifBlank { error("アルバムアートの画像をギャラリーから選び直してください。") }
    else -> video.thumbnail.ifBlank { youtubeThumbnail(video.url) }.ifBlank { null }
}

/** Map exactly one audio track and, when requested, one JPEG front cover into ID3v2.3. */
internal fun mp3EncodingArgs(audio: String, cover: String?): List<String> {
    val args=mutableListOf<String>()
    if(cover != null) args+=listOf("-i",cover)
    args+=listOf("-map","0:a:0","-c:a","libmp3lame","-b:a",audio.removeSuffix("bps"),"-id3v2_version","3")
    args+=if(cover == null) listOf("-vn") else listOf("-map","1:v:0","-c:v","copy",
        "-disposition:v:0","attached_pic","-metadata:s:v:0","title=Album cover","-metadata:s:v:0","comment=Cover (front)")
    return args
}

private fun writeCover(bitmap: Bitmap, target: File) {
    // JPEG covers work with common music players; flatten transparent pixels onto white.
    val scale=(max(bitmap.width,bitmap.height)/1024f).coerceAtLeast(1f)
    val width=(bitmap.width/scale).toInt().coerceAtLeast(1)
    val height=(bitmap.height/scale).toInt().coerceAtLeast(1)
    val flat=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
    try {
        Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(bitmap,null,Rect(0,0,width,height),Paint(Paint.FILTER_BITMAP_FLAG)) }
        target.outputStream().use { check(flat.compress(Bitmap.CompressFormat.JPEG,90,it)) { "画像を保存できませんでした。" } }
    } finally { flat.recycle() }
}

internal fun importArtwork(context: Context, uri: Uri): File {
    val target=File(File(context.filesDir,"artwork").apply { mkdirs() },"cover-${UUID.randomUUID()}.jpg")
    try {
        val bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,uri)) { decoder,info,_ ->
            val scale=(max(info.size.width,info.size.height)/1024f).coerceAtLeast(1f)
            decoder.setTargetSize((info.size.width/scale).toInt().coerceAtLeast(1),(info.size.height/scale).toInt().coerceAtLeast(1))
            decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try { writeCover(bitmap,target) } finally { bitmap.recycle() }
        return target
    } catch(e: Exception) { target.delete(); throw e }
}

internal fun prepareMp3Artwork(context: Context, video: Video, p: Preferences, work: File): File? {
    val source=artworkSource(video,p) ?: return null
    val cover=File(work,"cover.jpg")
    if(p.artworkMode == "ギャラリー") {
        val image=File(source)
        require(image.isFile && image.parentFile?.canonicalFile == File(context.filesDir,"artwork").canonicalFile) {
            "アルバムアートの画像がありません。ギャラリーから選び直してください。"
        }
        image.copyTo(cover,overwrite=true)
    } else {
        val bitmap=runBlocking { withTimeoutOrNull(20000) {
            val result=context.imageLoader.execute(ImageRequest.Builder(context).data(source).size(1024).allowHardware(false).build())
            (result as? SuccessResult)?.drawable?.toBitmap()
                ?: error("サムネイルを取得できません。再試行するか、アルバムアートを「なし」に変更してください。")
        } } ?: error("サムネイル取得がタイムアウトしました。再試行するか、アルバムアートを「なし」に変更してください。")
        // Coil may own/cache this bitmap: don't recycle it.
        writeCover(bitmap,cover)
    }
    return cover
}
