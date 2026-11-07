package io.github.l1zerox.pandora

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Draw a blurred copy only behind the labels, aligned with the original center-cropped image. */
@Composable
internal fun ThumbnailGlassLabels(site: String, duration: String, thumbnail: Bitmap?, tone: Color?,
                                  modifier: Modifier = Modifier) {
    val blurred by produceState<Bitmap?>(null,thumbnail) {
        value=null
        if(thumbnail != null) value=withContext(Dispatchers.Default) { blurThumbnail(thumbnail) }
    }
    val image=remember(blurred) { blurred?.asImageBitmap() }
    val tint=remember(tone) { readableGlassTint(tone ?: Color(0xFF4388FF)) }
    var root by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var siteBounds by remember { mutableStateOf(Rect.Zero) }
    var durationBounds by remember { mutableStateOf(Rect.Zero) }
    Box(modifier.onGloballyPositioned { root=it }.drawWithCache {
        val radius=14.dp.toPx()
        val paths=listOf(siteBounds,durationBounds).filter { it.width > 0 && it.height > 0 }.map {
            Path().apply { addRoundRect(RoundRect(it,CornerRadius(radius,radius))) }
        }
        val scale=image?.let { max(size.width/it.width,size.height/it.height) } ?: 1f
        val target=IntSize(((image?.width ?: 0)*scale).roundToInt(),((image?.height ?: 0)*scale).roundToInt())
        val offset=IntOffset(((size.width-target.width)/2).roundToInt(),((size.height-target.height)/2).roundToInt())
        onDrawBehind {
            for(path in paths) clipPath(path) {
                image?.let { drawImage(it,dstOffset=offset,dstSize=target,filterQuality=FilterQuality.Medium) }
                drawRect(tint)
            }
        }
    }) {
        Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(8.dp),
            verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            // Weight belongs to the transparent spacer container, never to the badge itself.
            Box(Modifier.weight(1f)) {
                GlassLabel(site,Modifier.testTag("thumbnail-site").onGloballyPositioned {
                    if(root?.isAttached == true && it.isAttached) siteBounds=root!!.localBoundingBoxOf(it,false)
                })
            }
            GlassLabel(duration,Modifier.testTag("thumbnail-duration").onGloballyPositioned {
                if(root?.isAttached == true && it.isAttached) durationBounds=root!!.localBoundingBoxOf(it,false)
            })
        }
    }
}

@Composable
private fun GlassLabel(text: String, modifier: Modifier) {
    Text(text,modifier.padding(horizontal=10.dp,vertical=6.dp),color=Color.White,
        style=MaterialTheme.typography.labelMedium,fontSize=13.sp,fontWeight=FontWeight.Medium,
        maxLines=1,overflow=TextOverflow.Ellipsis)
}

/** Small software blur works on Android 10/11 too, without reloading the thumbnail. */
internal fun blurThumbnail(source: Bitmap, maxEdge: Int = 320, passes: Int = 2): Bitmap {
    require(maxEdge > 0 && passes > 0)
    val scale=minOf(1f,maxEdge.toFloat()/max(source.width,source.height))
    val width=(source.width*scale).roundToInt().coerceAtLeast(1)
    val height=(source.height*scale).roundToInt().coerceAtLeast(1)
    val small=Bitmap.createScaledBitmap(source,width,height,true)
    val pixels=IntArray(width*height)
    small.getPixels(pixels,0,width,0,0,width,height)
    if(small !== source) small.recycle()
    blurThumbnailPixels(pixels,width,height,passes)
    return Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888)
}

internal fun blurThumbnailPixels(pixels: IntArray,width: Int,height: Int, passes: Int = 2) {
    require(width > 0 && height > 0 && pixels.size == width*height && passes > 0)
    val scratch=IntArray(pixels.size)
    val radius=3
    // Separable box blur; fixed work per pixel and no per-pixel allocation.
    repeat(passes) {
        for(horizontal in listOf(true,false)) {
            val input=if(horizontal) pixels else scratch
            val output=if(horizontal) scratch else pixels
            val lines=if(horizontal) height else width
            val length=if(horizontal) width else height
            val stride=if(horizontal) 1 else width
            for(line in 0 until lines) {
                val base=if(horizontal) line*width else line
                var a=0; var r=0; var g=0; var b=0
                for(delta in -radius..radius) {
                    val color=input[base+delta.coerceIn(0,length-1)*stride]
                    a+=color ushr 24; r+=(color ushr 16) and 255; g+=(color ushr 8) and 255; b+=color and 255
                }
                val count=radius*2+1
                for(position in 0 until length) {
                    output[base+position*stride]=((a/count) shl 24) or ((r/count) shl 16) or ((g/count) shl 8) or (b/count)
                    val old=input[base+(position-radius).coerceIn(0,length-1)*stride]
                    val next=input[base+(position+radius+1).coerceIn(0,length-1)*stride]
                    a+=(next ushr 24)-(old ushr 24); r+=((next ushr 16) and 255)-((old ushr 16) and 255)
                    g+=((next ushr 8) and 255)-((old ushr 8) and 255); b+=(next and 255)-(old and 255)
                }
            }
        }
    }
}
