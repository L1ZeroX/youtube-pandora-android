package io.github.l1zerox.pandora

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibrarySeekBar(uri: String, playback: LibraryPlayback, palette: LibraryPalette, source: Color?, backdrop: LibraryBackdrop,
    compact: Boolean = false) {
    var drag by remember(uri) { mutableStateOf<Float?>(null) }
    val duration=playback.duration
    val fraction=drag ?: playbackFraction(playback.position,duration)
    val enabled=playback.controller != null && playback.seekable
    val settings by Store.state.collectAsStateWithLifecycle()
    val bass=settings.preferences.libraryPlayerWave && PlaybackEffect.fromId(settings.preferences.libraryPlayerEffect) == PlaybackEffect.BASS
    LaunchedEffect(enabled) { if(!enabled) drag=null }
    val accent=remember(source,palette,backdrop) {
        val tint=libraryGlassTint(palette,backdrop.contrastColors,backdrop.transparency)
        (0..20).asSequence().map { lerp(source ?: palette.content,palette.content,it/20f) }
            .first { color -> backdrop.contrastColors.all { libraryContrast(color,tint.compositeOver(it)) >= 3f } }
    }
    Column {
        if(compact || bass) {
            PlaybackPositionSlider(mediaKey=uri,value=fraction,onValueChange={drag=it},
                onValueChangeFinished={drag?.let { playback.seek(uri,it) }; drag=null},enabled=enabled,
                color=accent,description="再生位置",modifier=Modifier.fillMaxWidth(),compact=true,
                waveform=bass,playing=playback.panel.advancing && drag == null,effect=if(bass) PlaybackEffect.BASS else PlaybackEffect.WAVE)
        } else key(uri) {
            Slider(value=fraction.coerceIn(0f,1f),onValueChange={drag=it},
                onValueChangeFinished={drag?.let { playback.seek(uri,it) }; drag=null},
                enabled=enabled,
                modifier=Modifier.fillMaxWidth().height(32.dp).semantics { contentDescription="再生位置" },
                colors=SliderDefaults.colors(thumbColor=accent,activeTrackColor=accent,
                    inactiveTrackColor=accent.copy(alpha=.22f)))
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(durationText((if(drag != null) playbackPositionMs(duration,fraction) else playback.position)/1000.0),fontSize=11.sp,color=palette.detail)
            Text(if(duration > 0) durationText(duration/1000.0) else "--:--",fontSize=11.sp,color=palette.detail)
        }
    }
}

@Composable
internal fun LibraryPlayButton(uri: String, playback: LibraryPlayback, palette: LibraryPalette, onPlay: ()->Unit, outlined: Boolean = false) {
    val active=uri == playback.uri
    val playing=active && playback.playing
    IconButton(onClick={if(active) playback.toggle(uri) else onPlay()},enabled=!active || playback.controller != null,
        modifier=Modifier.semantics { contentDescription=if(playing) "一時停止" else "再生" }) {
        LibraryPlayGlyph(playing,palette.content,outlined)
    }
}

@Composable
internal fun LibraryPlayGlyph(playing: Boolean, color: Color, outlined: Boolean) {
    if(outlined) Box(Modifier.size(32.dp).border(1.5.dp,color,CircleShape),contentAlignment=Alignment.Center) {
        Text(if(playing) "Ⅱ" else "▶",fontSize=16.sp,color=color)
    } else Text(if(playing) "Ⅱ" else "▶",fontSize=22.sp,color=color)
}
