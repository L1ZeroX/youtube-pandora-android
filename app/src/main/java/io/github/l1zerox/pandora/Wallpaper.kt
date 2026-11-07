package io.github.l1zerox.pandora

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

internal fun appBackgroundColor(dark: Boolean) = if(dark) Color(0xFF090B10) else Color(0xFFF3F4F6)
internal fun appSurfaceColor(dark: Boolean) = if(dark) Color(0xFF191C23) else Color.White

/** Shared by downloads and library so decoding, cropping and preferences cannot drift. */
@Composable
internal fun rememberWallpaper(path: String): State<Bitmap?> = produceState<Bitmap?>(null,path) {
    value=null
    value=withContext(Dispatchers.IO) {
        runCatching {
            val file=File(path)
            if(!file.isFile) null else ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder,info,_ ->
                val scale=(max(info.size.width,info.size.height)/1600f).coerceAtLeast(1f)
                decoder.setTargetSize((info.size.width/scale).toInt(),(info.size.height/scale).toInt())
                decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull()
    }
}
