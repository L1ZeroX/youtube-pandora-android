package io.github.l1zerox.pandora

import android.view.View
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as MediaUiR

/** One artwork-derived accent for both playback buttons and the seek bar. */
internal fun playbackAccent(source: Color?): Color {
    val original=(source ?: Color(0xFF4388FF)).copy(alpha=1f)
    return (0..20).asSequence().map { lerp(original,Color.White,it/20f) }
        .first { libraryContrast(it,Color(0xFF242424)) >= 3f }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun stylePlaybackControls(view: PlayerView, source: Color?) {
    val tint=artworkGlassTint(source)
    val seekColor=playbackAccent(source)
    // This translucent backing bounds video brightness so colored controls remain visible.
    view.findViewById<View>(MediaUiR.id.exo_center_controls)?.background=GradientDrawable().apply {
        setColor(0xDB000000.toInt()); cornerRadius=24*view.resources.displayMetrics.density
    }
    for(id in listOf(MediaUiR.id.exo_play_pause,MediaUiR.id.exo_prev,MediaUiR.id.exo_next))
        view.findViewById<ImageView>(id)?.imageTintList=ColorStateList.valueOf(seekColor.toArgb())
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
