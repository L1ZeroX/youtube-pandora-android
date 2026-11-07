package io.github.l1zerox.pandora

import android.graphics.Bitmap
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal data class LibraryBackdrop(val bitmap: Bitmap?, val bounds: Rect, val opacity: Float, val base: Color) {
    // Check the actual blurred wallpaper extremes, including the sheen, instead of assuming a white wall.
    val contrastColors: List<Color> by lazy {
        val colors=mutableListOf(base)
        bitmap?.let { image ->
            val pixels=IntArray(image.width*image.height)
            image.getPixels(pixels,0,image.width,0,0,image.width,image.height)
            pixels.forEach { pixel -> colors+=Color(pixel).let { it.copy(alpha=it.alpha*(1-opacity.coerceIn(0f,1f))) }.compositeOver(base) }
        }
        val darkest=colors.minBy { it.luminance() }
        val lightest=Color.White.copy(alpha=.12f).compositeOver(colors.maxBy { it.luminance() })
        listOf(darkest,lightest)
    }
}

/** Same center-crop as WallpaperLayer, translated into the moving card's local coordinates. */
internal fun libraryBackdropPlacement(image: IntSize, viewport: Rect, cardPosition: Offset): Pair<IntOffset,IntSize> {
    require(image.width > 0 && image.height > 0 && viewport.width > 0 && viewport.height > 0)
    val scale=maxOf(viewport.width/image.width,viewport.height/image.height)
    val size=IntSize((image.width*scale).roundToInt().coerceAtLeast(1),(image.height*scale).roundToInt().coerceAtLeast(1))
    val offset=IntOffset((viewport.left-cardPosition.x+(viewport.width-size.width)/2).roundToInt(),
        (viewport.top-cardPosition.y+(viewport.height-size.height)/2).roundToInt())
    return offset to size
}

@Composable
internal fun Modifier.libraryCardGlass(palette: LibraryPalette, backdrop: LibraryBackdrop): Modifier {
    var position by remember { mutableStateOf(Offset.Zero) }
    val image=remember(backdrop.bitmap) { backdrop.bitmap?.asImageBitmap() }
    val tint=remember(palette,backdrop) { libraryGlassTint(palette,backdrop.contrastColors) }
    return clip(RoundedCornerShape(16.dp)).onGloballyPositioned { position=it.positionInWindow() }.drawWithCache {
        val placement=image?.takeIf { backdrop.bounds.width > 0 && backdrop.bounds.height > 0 }?.let {
            libraryBackdropPlacement(IntSize(it.width,it.height),backdrop.bounds,position)
        }
        val sheen=Brush.verticalGradient(listOf(Color.White.copy(alpha=.12f),Color.Transparent))
        onDrawBehind {
            drawRect(backdrop.base)
            if(image != null && placement != null) drawImage(image,dstOffset=placement.first,dstSize=placement.second,
                alpha=1-backdrop.opacity.coerceIn(0f,1f),filterQuality=FilterQuality.Medium)
            drawRect(sheen)
            drawRect(tint)
        }
    }
}
