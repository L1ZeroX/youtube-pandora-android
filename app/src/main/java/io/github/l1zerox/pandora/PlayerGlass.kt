package io.github.l1zerox.pandora

import android.view.View
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as MediaUiR

@androidx.annotation.OptIn(UnstableApi::class)
internal fun stylePlaybackControls(view: PlayerView, source: Color?) {
    val tint=artworkGlassTint(source)
    val original=(source ?: Color(0xFF4388FF)).copy(alpha=1f)
    // Preserve the thumbnail color, lifting only very dark colors off the black player canvas.
    val seekColor=(0..20).asSequence().map { lerp(original,Color.White,it/20f) }
        .first { libraryContrast(it,Color.Black) >= 3f }
    fun glass()=ArtworkGlassDrawable(null,tint,view.resources.displayMetrics.density)
    view.findViewById<View>(MediaUiR.id.exo_bottom_bar)?.background=glass()
    for(id in listOf(MediaUiR.id.exo_position,MediaUiR.id.exo_duration))
        view.findViewById<TextView>(id)?.setTextColor(android.graphics.Color.WHITE)
    view.findViewById<DefaultTimeBar>(MediaUiR.id.exo_progress)?.apply {
        background=null
        setPlayedColor(seekColor.toArgb())
        setScrubberColor(seekColor.toArgb())
        setBufferedColor(seekColor.copy(alpha=.6f).toArgb())
        setUnplayedColor(seekColor.copy(alpha=.22f).toArgb())
    }
}
