package io.github.l1zerox.pandora

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal const val DEFAULT_LIBRARY_PLAYER_SCALE=.7f
internal fun libraryPlayerScale(value: Float)=if(value.isFinite()) value.coerceIn(.7f,1f) else DEFAULT_LIBRARY_PLAYER_SCALE

internal data class LibraryPlayerState(val uri: String = "", val title: String = "", val artist: String = "",
    val playing: Boolean = false, val position: Long = 0, val duration: Long = 0,
    val seekable: Boolean = false, val canPlay: Boolean = false, val canPrevious: Boolean = false, val canNext: Boolean = false)

// At least this much shade covers every artwork pixel, including completely white covers.
internal const val LIBRARY_PLAYER_SHADE=.62f
internal fun libraryPlayerAccent(source: Color?): Color {
    val brightest=Color.Black.copy(alpha=LIBRARY_PLAYER_SHADE).compositeOver(Color.White)
    return (0..20).asSequence().map { lerp((source ?: Color(0xFF4388FF)).copy(alpha=1f),Color.White,it/20f) }
        .first { libraryContrast(it,brightest) >= 3f && libraryContrast(it,Color.Black) >= 3f }
}

/** Artwork stays behind the controls. Hiding this UI never stops or replaces the playback session. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryPlayerPanel(state: LibraryPlayerState, artwork: Bitmap?, source: Color?, scale: Float,
    onOpen: ()->Unit, onToggle: ()->Unit, onPrevious: ()->Unit, onNext: ()->Unit, onSeek: (Float)->Unit,
    modifier: Modifier = Modifier) {
    val size=libraryPlayerScale(scale)
    val accent=remember(source) { libraryPlayerAccent(source) }
    var drag by remember(state.uri) { mutableStateOf<Float?>(null) }
    LaunchedEffect(state.seekable) { if(!state.seekable) drag=null }
    val fraction=drag ?: playbackFraction(state.position,state.duration)
    val shape=RoundedCornerShape((28*size).dp)
    Box(modifier.fillMaxWidth().clip(shape).background(source ?: Color(0xFF263746))
        .border(1.dp,Color.White.copy(alpha=.12f),shape).testTag("library-player-panel")) {
        if(artwork != null) Image(artwork.asImageBitmap(),null,Modifier.matchParentSize(),contentScale=ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
            Color.Black.copy(alpha=LIBRARY_PLAYER_SHADE),Color.Black.copy(alpha=.78f)))))
        Column(Modifier.fillMaxWidth().padding(horizontal=(16*size).dp,vertical=(6*size).dp)) {
            Column(Modifier.fillMaxWidth().heightIn(min=(32*size).dp).clickable(onClick=onOpen)
                .semantics { contentDescription="再生画面を開く" }.testTag("library-player-title")) {
                Text(state.title.ifBlank { "再生中" },fontSize=(20*size).sp,lineHeight=(26*size).sp,
                    fontWeight=FontWeight.SemiBold,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis)
                if(state.artist.isNotBlank() && size >= .85f) Text(state.artist,fontSize=12.sp,lineHeight=16.sp,
                    color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            // Reduce the visual row, not the slider's coordinate space. Its marker and touch value
            // still share SliderState; the three transport buttons keep full 48 dp touch targets.
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                PlaybackPositionSlider(mediaKey=state.uri,value=fraction,onValueChange={drag=it},
                    onValueChangeFinished={drag?.let(onSeek); drag=null},enabled=state.seekable,color=accent,
                    description="再生パネルの再生位置",modifier=Modifier.fillMaxWidth(),compact=true)
            }
            val elapsed=durationText((if(drag != null) playbackPositionMs(state.duration,fraction) else state.position)/1000.0)
            val total=if(state.duration > 0) durationText(state.duration/1000.0) else "--:--"
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val separateTime=maxWidth < 268.dp || LocalDensity.current.fontScale > 1.2f
                Column {
                    if(separateTime) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(elapsed,fontSize=11.sp,color=Color.White)
                        Text(total,fontSize=11.sp,color=Color.White)
                    }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                        if(!separateTime) Text(elapsed,Modifier.weight(1f),fontSize=11.sp,color=Color.White,maxLines=1)
                        LibraryPanelButton(R.drawable.ic_panel_previous,"再生パネルの前へ",state.canPrevious,accent,onPrevious,size)
                        LibraryPanelButton(if(state.playing) R.drawable.ic_panel_pause else R.drawable.ic_panel_play,
                            if(state.playing) "再生パネルを一時停止" else "再生パネルで再生",state.canPlay,accent,onToggle,size)
                        LibraryPanelButton(R.drawable.ic_panel_next,"再生パネルの次へ",state.canNext,accent,onNext,size)
                        if(!separateTime) Text(total,Modifier.weight(1f),fontSize=11.sp,color=Color.White,maxLines=1,textAlign=TextAlign.End)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryPanelButton(icon: Int, description: String, enabled: Boolean, accent: Color, onClick: ()->Unit, scale: Float) {
    IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(48.dp),
        colors=IconButtonDefaults.iconButtonColors(contentColor=accent,disabledContentColor=accent.copy(alpha=.35f))) {
        Icon(painterResource(icon),description,Modifier.size((36*scale).dp))
    }
}
