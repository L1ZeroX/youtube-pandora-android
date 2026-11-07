package io.github.l1zerox.pandora

import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max

/** Sample actual RGB colors: neutral artwork must never acquire an artificial hue. */
internal fun libraryThumbnailColor(pixels: IntArray): Color? {
    val opaque=pixels.filter { (it ushr 24) >= 128 }
    if(opaque.isEmpty()) return null
    fun chromatic(pixel: Int): Boolean {
        val r=(pixel ushr 16) and 255; val g=(pixel ushr 8) and 255; val b=pixel and 255
        val hi=maxOf(r,g,b); val lo=minOf(r,g,b)
        return hi >= 32 && (hi-lo).toFloat()/hi >= .18f
    }
    val colored=opaque.filter(::chromatic)
    // Ignore black letterboxing when there is a meaningful amount of colored image content.
    val samples=if(colored.size >= opaque.size*.03f) colored else opaque
    val buckets=samples.groupBy { ((it ushr 20) and 15)*256+((it ushr 12) and 15)*16+((it ushr 4) and 15) }
    val dominant=buckets.values.maxBy { it.size }
    return Color(dominant.sumOf { (it ushr 16) and 255 }/dominant.size,
        dominant.sumOf { (it ushr 8) and 255 }/dominant.size,dominant.sumOf { it and 255 }/dominant.size)
}

internal fun libraryThumbnailColor(bitmap: Bitmap): Color? {
    val pixels=ArrayList<Int>(1024)
    for(y in 0 until bitmap.height step max(1,bitmap.height/32))
        for(x in 0 until bitmap.width step max(1,bitmap.width/32)) pixels+=bitmap.getPixel(x,y)
    return libraryThumbnailColor(pixels.toIntArray())
}

internal fun libraryContrast(a: Color, b: Color): Float =
    (maxOf(a.luminance(),b.luminance())+.05f)/(minOf(a.luminance(),b.luminance())+.05f)

internal fun libraryTextColor(background: Color): Color =
    if(libraryContrast(Color.White,background) >= libraryContrast(Color.Black,background)) Color.White else Color.Black

internal data class LibraryPalette(val surface: Color, val content: Color, val detail: Color)
internal fun libraryPalette(source: Color?, dark: Boolean): LibraryPalette {
    val color=(source ?: Color(0xFF6B7280)).copy(alpha=1f)
    val surface=lerp(color,if(dark) Color.Black else Color.White,if(dark) .45f else .78f)
    val content=libraryTextColor(surface)
    val softer=lerp(content,surface,.14f)
    return LibraryPalette(surface,content,if(libraryContrast(softer,surface) >= 4.5f) softer else content)
}

/** Keep the existing RGB palette; only increase opacity when wallpaper would obscure text. */
internal fun libraryPanelTransparency(value: Float): Float = if(value.isFinite()) value.coerceIn(0f,.85f) else .45f

internal fun libraryGlassTint(palette: LibraryPalette, backgrounds: List<Color> = listOf(Color.Black,Color.White), transparency: Float = .45f): Color {
    val alpha=1-libraryPanelTransparency(transparency)
    for(step in 0..34) {
        val tint=palette.surface.copy(alpha=(alpha+step*.025f).coerceAtMost(1f))
        if(backgrounds.all { backdrop ->
            val background=tint.compositeOver(backdrop)
            libraryContrast(palette.content,background) >= 4.5f && libraryContrast(palette.detail,background) >= 4.5f
        }) return tint
    }
    return palette.surface
}

/** Active artwork also tints controls; all active text pairs retain at least 4.5:1 contrast. */
internal fun libraryColorScheme(source: Color?, dark: Boolean, backgroundOverride: Color? = null): ColorScheme {
    val base=if(dark) darkColorScheme() else lightColorScheme()
    if(source == null) return base.copy(background=backgroundOverride ?: base.background)
    val palette=libraryPalette(source,dark)
    val background=backgroundOverride ?: lerp(source.copy(alpha=1f),if(dark) Color.Black else Color.White,.96f)
    val foreground=libraryTextColor(background)
    var primary=source.copy(alpha=1f)
    repeat(32) { if(libraryContrast(primary,background) < 4.5f) primary=lerp(primary,foreground,.12f) }
    if(libraryContrast(primary,background) < 4.5f) primary=foreground
    return base.copy(background=background,onBackground=foreground,surface=background,onSurface=foreground,
        primary=primary,onPrimary=libraryTextColor(primary),primaryContainer=palette.surface,onPrimaryContainer=palette.content,
        secondaryContainer=palette.surface,onSecondaryContainer=palette.content,
        outline=lerp(foreground,background,.35f))
}
