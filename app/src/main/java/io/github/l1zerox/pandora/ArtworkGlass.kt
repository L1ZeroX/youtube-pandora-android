package io.github.l1zerox.pandora

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity

internal fun artworkGlassTint(source: Color?): Color = readableGlassTint(source ?: Color(0xFF4388FF))
internal fun artworkGlassAccent(source: Color?): Color {
    val accent=lerp(source ?: Color(0xFF4388FF),Color.White,.72f)
    return if(libraryContrast(accent,artworkGlassTint(source).compositeOver(Color.White)) >= 3f) accent else Color.White
}

/** A blurred artwork backplate with a translucent tint, usable by Compose and Media3 Views. */
internal class ArtworkGlassDrawable(bitmap: Bitmap?, tint: Color, private val density: Float): Drawable() {
    private val area=RectF()
    private val image=bitmap?.let { BitmapShader(it,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP) }
    private val size=bitmap?.let { it.width to it.height }
    private val imagePaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader=image; alpha=170 }
    private val tintPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=tint.toArgb() }
    private val rim=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0x30FFFFFF; style=Paint.Style.STROKE; strokeWidth=density }
    override fun onBoundsChange(bounds: Rect) {
        area.set(bounds)
        size?.let { (width,height) ->
            val scale=maxOf(area.width()/width,area.height()/height)
            val matrix=Matrix().apply { setScale(scale,scale); postTranslate(area.centerX()-width*scale/2,area.centerY()-height*scale/2) }
            image?.setLocalMatrix(matrix)
        }
    }
    override fun draw(canvas: Canvas) {
        val radius=16*density
        if(image != null) canvas.drawRoundRect(area,radius,radius,imagePaint)
        canvas.drawRoundRect(area,radius,radius,tintPaint)
        canvas.drawRoundRect(area,radius,radius,rim)
    }
    override fun setAlpha(alpha: Int) { /* The translucent layers have fixed contrast-safe alpha. */ }
    override fun setColorFilter(colorFilter: ColorFilter?) { }
    @Deprecated("Deprecated in Android") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

@Composable
internal fun ArtworkGlass(blurred: Bitmap?, source: Color?, modifier: Modifier = Modifier, content: @Composable BoxScope.()->Unit) {
    val density=LocalDensity.current.density
    val background=remember(blurred,source,density) { ArtworkGlassDrawable(blurred,artworkGlassTint(source),density) }
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(modifier.drawWithCache {
            onDrawBehind {
                background.setBounds(0,0,size.width.toInt(),size.height.toInt())
                drawIntoCanvas { background.draw(it.nativeCanvas) }
            }
        },content=content)
    }
}
