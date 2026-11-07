package io.github.l1zerox.pandora

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.R as MediaUiR

internal data class MusicPalette(val top: Color, val bottom: Color, val accent: Color, val onAccent: Color, val secondary: Color)
internal fun musicPalette(source: Color?): MusicPalette {
    val original=(source ?: Color(0xFF4388FF)).copy(alpha=1f)
    val top=(0..20).asSequence().map { lerp(original,Color.Black,.45f+it*.0275f) }
        .first { libraryContrast(Color.White,it) >= 4.5f }
    val bottom=lerp(top,Color.Black,.88f)
    val accent=(0..20).asSequence().map { lerp(original,Color.White,it/20f) }
        .first { libraryContrast(it,top) >= 3f && libraryContrast(it,bottom) >= 3f }
    return MusicPalette(top,bottom,accent,libraryTextColor(accent),
        if(libraryContrast(Color.LightGray,top) >= 4.5f) Color.LightGray else Color.White)
}

@Composable
internal fun MusicPlayerScreen(player: Player?, title: String, artwork: Bitmap?, source: Color?, error: String,
    onBack: ()->Unit, onStop: ()->Unit) {
    val transport=rememberMusicPlayback(player)
    val palette=remember(source) { musicPalette(source) }
    var menu by remember { mutableStateOf(false) }
    var queue by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(palette.top,lerp(palette.top,palette.bottom,.55f),palette.bottom)))) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val wide=maxWidth > maxHeight
            val artSize=if(wide) minOf((maxHeight-104.dp).coerceAtLeast(128.dp),maxWidth*.42f)
                else minOf((maxWidth-48.dp).coerceAtLeast(160.dp),(maxHeight-310.dp).coerceAtLeast(180.dp),480.dp)
            Column(Modifier.fillMaxWidth().then(if(wide) Modifier.fillMaxHeight() else Modifier.verticalScroll(rememberScrollState()).heightIn(min=maxHeight))
                .padding(horizontal=24.dp,vertical=8.dp),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick=onBack,modifier=Modifier.semantics { contentDescription="ライブラリーに戻る" }) {
                        Text("⌄",fontSize=32.sp,color=Color.White)
                    }
                    Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("ライブラリーから再生中",fontSize=11.sp,color=palette.secondary)
                        Text("音楽",fontWeight=FontWeight.SemiBold,color=Color.White)
                    }
                    Box {
                        IconButton(onClick={menu=true},modifier=Modifier.semantics { contentDescription="再生メニュー" }) {
                            Text("⋮",fontSize=26.sp,color=Color.White)
                        }
                        DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                            DropdownMenuItem(text={Text("再生リスト")},onClick={menu=false; queue=true})
                            DropdownMenuItem(text={Text("停止")},enabled=player != null,onClick={menu=false; onStop()})
                        }
                    }
                }
                if(wide) Row(Modifier.weight(1f).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                    MusicArtwork(artwork,palette,Modifier.size(artSize))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        MusicControls(title,error,transport,palette)
                        TextButton(onClick={queue=true},modifier=Modifier.align(Alignment.End)) { Text("再生リスト",color=palette.secondary) }
                    }
                } else {
                    MusicArtwork(artwork,palette,Modifier.padding(vertical=20.dp).size(artSize))
                    Column(Modifier.fillMaxWidth()) {
                        MusicControls(title,error,transport,palette)
                        Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.End) {
                            TextButton(onClick={queue=true}) { Text("再生リスト",color=palette.secondary) }
                        }
                    }
                }
            }
        }
    }
    if(queue) AlertDialog(onDismissRequest={queue=false},title={Text("再生リスト")},
        text={LazyColumn(Modifier.heightIn(max=420.dp)) {
            items(transport.state.queue,key={it.uri}) { item ->
                TextButton(onClick={transport.select(item.uri); queue=false},enabled=transport.state.canSelect,
                    modifier=Modifier.fillMaxWidth()) {
                    Text((if(item.uri == transport.state.uri) "▶  " else "")+item.title,Modifier.fillMaxWidth(),
                        textAlign=TextAlign.Start,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
        }},confirmButton={TextButton(onClick={queue=false}) { Text("閉じる") }})
}

@Composable
private fun MusicArtwork(artwork: Bitmap?, palette: MusicPalette, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(palette.bottom),contentAlignment=Alignment.Center) {
        if(artwork != null) Image(artwork.asImageBitmap(),"アルバムアート",Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
        else Text("♪",fontSize=80.sp,color=palette.accent)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicControls(title: String, error: String, transport: MusicPlayback, palette: MusicPalette) {
    val state=transport.state
    var drag by remember(state.uri) { mutableStateOf<Float?>(null) }
    val fraction=drag ?: playbackFraction(state.position,state.duration)
    LaunchedEffect(state.seekable) { if(!state.seekable) drag=null }
    Column(Modifier.fillMaxWidth()) {
        Text(title,fontSize=22.sp,fontWeight=FontWeight.Bold,color=Color.White,maxLines=2,overflow=TextOverflow.Ellipsis)
        Text(state.artist.ifBlank { "音楽 · ライブラリー" },fontSize=14.sp,color=palette.secondary,maxLines=1,overflow=TextOverflow.Ellipsis)
        if(error.isNotBlank()) Text(error,color=Color.White,fontSize=13.sp,modifier=Modifier.padding(top=8.dp))
        PlaybackPositionSlider(mediaKey=state.uri,value=fraction,onValueChange={drag=it},
            onValueChangeFinished={drag?.let { transport.seek(state.uri,it) }; drag=null},enabled=state.seekable,
            color=palette.accent,description="音楽の再生位置",modifier=Modifier.fillMaxWidth().padding(top=12.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(durationText((if(drag != null) playbackPositionMs(state.duration,fraction) else state.position)/1000.0),fontSize=12.sp,color=palette.secondary)
            Text(if(state.duration > 0) durationText(state.duration/1000.0) else "--:--",fontSize=12.sp,color=palette.secondary)
        }
        Row(Modifier.fillMaxWidth().padding(top=16.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            MusicIconButton(MediaUiR.drawable.exo_icon_shuffle_on,"シャッフル",state.canShuffle,
                if(state.shuffle) palette.accent else palette.secondary,transport::shuffle,
                if(state.shuffle) "オン" else "オフ")
            MusicIconButton(MediaUiR.drawable.exo_icon_previous,"前の曲",state.canPrevious,palette.accent,transport::previous)
            FilledIconButton(onClick=transport::toggle,enabled=state.canPlay,shape=CircleShape,modifier=Modifier.size(68.dp),
                colors=IconButtonDefaults.filledIconButtonColors(containerColor=palette.accent,contentColor=palette.onAccent)) {
                Icon(painterResource(if(state.playing) MediaUiR.drawable.exo_icon_pause else MediaUiR.drawable.exo_icon_play),
                    if(state.playing) "一時停止" else "再生",Modifier.size(38.dp))
            }
            MusicIconButton(MediaUiR.drawable.exo_icon_next,"次の曲",state.canNext,palette.accent,transport::next)
            MusicIconButton(if(state.repeat == Player.REPEAT_MODE_ONE) MediaUiR.drawable.exo_icon_repeat_one else MediaUiR.drawable.exo_icon_repeat_all,
                "リピート",state.canRepeat,if(state.repeat != Player.REPEAT_MODE_OFF) palette.accent else palette.secondary,transport::repeat,
                when(state.repeat) { Player.REPEAT_MODE_ALL -> "全曲"; Player.REPEAT_MODE_ONE -> "1曲"; else -> "オフ" })
        }
    }
}

@Composable
private fun MusicIconButton(@DrawableRes icon: Int, label: String, enabled: Boolean, color: Color, onClick: ()->Unit, state: String? = null) {
    IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(48.dp).semantics { if(state != null) stateDescription=state },
        colors=IconButtonDefaults.iconButtonColors(contentColor=color,disabledContentColor=color.copy(alpha=.35f))) {
        Icon(painterResource(icon),label,Modifier.size(28.dp))
    }
}
