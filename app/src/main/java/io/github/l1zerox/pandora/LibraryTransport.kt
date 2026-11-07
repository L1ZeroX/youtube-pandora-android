package io.github.l1zerox.pandora

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun LibrarySeekBar(uri: String, playback: LibraryPlayback, palette: LibraryPalette, source: Color?, backdrop: LibraryBackdrop) {
    var drag by remember(uri) { mutableStateOf<Float?>(null) }
    val duration=playback.duration
    val fraction=drag ?: if(duration > 0) playback.position.toFloat()/duration else 0f
    val accent=remember(source,palette,backdrop) {
        val tint=libraryGlassTint(palette,backdrop.contrastColors,backdrop.transparency)
        (0..20).asSequence().map { lerp(source ?: palette.content,palette.content,it/20f) }
            .first { color -> backdrop.contrastColors.all { libraryContrast(color,tint.compositeOver(it)) >= 3f } }
    }
    Column {
        Slider(value=fraction.coerceIn(0f,1f),onValueChange={drag=it},
            onValueChangeFinished={drag?.let { playback.seek(uri,it) }; drag=null},
            enabled=playback.controller != null && playback.seekable,
            modifier=Modifier.fillMaxWidth().height(32.dp).semantics { contentDescription="再生位置" },
            colors=SliderDefaults.colors(thumbColor=accent,activeTrackColor=accent,
                inactiveTrackColor=accent.copy(alpha=.22f)))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(durationText((if(drag != null) (duration*fraction).toLong() else playback.position)/1000.0),fontSize=11.sp,color=palette.detail)
            Text(if(duration > 0) durationText(duration/1000.0) else "--:--",fontSize=11.sp,color=palette.detail)
        }
    }
}

@Composable
internal fun LibraryPlayButton(uri: String, playback: LibraryPlayback, palette: LibraryPalette, onPlay: ()->Unit) {
    val active=uri == playback.uri
    val playing=active && playback.playing
    IconButton(onClick={if(active) playback.toggle(uri) else onPlay()},enabled=!active || playback.controller != null,
        modifier=Modifier.semantics { contentDescription=if(playing) "一時停止" else "再生" }) {
        Text(if(playing) "Ⅱ" else "▶",fontSize=22.sp,color=palette.content)
    }
}
