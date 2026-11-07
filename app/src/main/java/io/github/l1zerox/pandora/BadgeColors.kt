package io.github.l1zerox.pandora

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver

/** Opaque image tint with at least 4.5:1 contrast against white badge text. */
internal fun readableBadgeColor(source: Color): Color {
    var color = source.copy(alpha=1f)
    repeat(32) {
        if(1.05f / (color.luminance() + .05f) >= 4.5f) return color
        color = lerp(color,Color.Black,.08f)
    }
    return Color.Black
}

internal fun strongBadgeColor(source: Color): Color = lerp(readableBadgeColor(source),Color.Black,.32f)

/** Translucent thumbnail labels must keep white text readable even over a white image. */
internal fun readableGlassTint(source: Color): Color {
    var color=source.copy(alpha=1f)
    repeat(32) {
        val tint=color.copy(alpha=.68f)
        if(1.05f/(tint.compositeOver(Color.White).luminance()+.05f) >= 4.5f) return tint
        color=lerp(color,Color.Black,.1f)
    }
    return Color.Black.copy(alpha=.68f)
}
