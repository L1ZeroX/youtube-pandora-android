package io.github.l1zerox.pandora

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import kotlin.math.abs

@Composable
internal fun PlaybackBetaMenu(player: Player?) {
    var menu by remember { mutableStateOf(false) }
    var queue by rememberSaveable { mutableStateOf(false) }
    var timer by rememberSaveable { mutableStateOf(false) }
    val status by PlaybackBetaRuntime.timer.collectAsStateWithLifecycle()
    Box {
        IconButton(onClick={menu=true},modifier=Modifier.semantics { contentDescription="再生の便利機能" }) { Text(if(status.active) "◷" else "⋮") }
        DropdownMenu(menu,{menu=false}) {
            DropdownMenuItem(text={Text("再生順を編集")},onClick={menu=false; queue=true})
            DropdownMenuItem(text={Text("スリープタイマー · ${status.label}")},onClick={menu=false; timer=true})
        }
    }
    if(queue) PlaybackQueueDialog(player) { queue=false }
    if(timer) SleepTimerDialog { timer=false }
}

@Composable
internal fun SleepTimerDialog(onDismiss: ()->Unit) {
    val status by PlaybackBetaRuntime.timer.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest=onDismiss,title={Text("スリープタイマー")},text={Column {
        Text(status.label)
        listOf(15 to "15分後",30 to "30分後",60 to "60分後",-1 to "この曲・動画の終了まで",0 to "オフ").forEach { (minutes,label) ->
            TextButton(onClick={PlaybackBetaRuntime.setTimer?.invoke(minutes); onDismiss()},
                enabled=PlaybackBetaRuntime.setTimer != null,modifier=Modifier.fillMaxWidth()) { Text(label) }
        }
        Text("時間になると再生を一時停止します。")
    }},confirmButton={TextButton(onClick=onDismiss) { Text("閉じる") }})
}

internal fun movePlaybackItem(player: Player, uri: String, offset: Int): Boolean {
    if(!player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) return false
    val index=(0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == uri } ?: return false
    val target=(index+offset).coerceIn(0,player.mediaItemCount-1)
    if(index == target) return false
    if(player.shuffleModeEnabled) {
        if(!player.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)) return false
        player.shuffleModeEnabled=false
    }
    player.moveMediaItem(index,target)
    return true
}

@Composable
internal fun PlaybackQueueDialog(player: Player?, onDismiss: ()->Unit) {
    val transport=rememberMusicPlayback(player)
    val threshold=with(LocalDensity.current) { 72.dp.toPx() }
    val editable=player?.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS) == true
    AlertDialog(onDismissRequest=onDismiss,title={Text("再生順を編集")},text={Column {
        Text("≡を長押しして移動。↑↓でも変更できます。")
        Text("並び替え・次に再生はシャッフルをオフにします。",style=MaterialTheme.typography.bodySmall)
        if(transport.state.queue.isEmpty()) Text("再生中の項目はありません。")
        LazyColumn(Modifier.heightIn(max=420.dp)) {
            items(transport.state.queue,key={it.uri}) { item ->
                Row(Modifier.fillMaxWidth().height(72.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("≡",Modifier.width(36.dp).fillMaxHeight().wrapContentHeight(Alignment.CenterVertically)
                        .semantics { contentDescription="${item.title}の順序をドラッグで変更" }
                        .pointerInput(item.uri,player,editable) {
                            if(editable) {
                                var distance=0f
                                detectDragGesturesAfterLongPress(onDragStart={distance=0f},onDragEnd={distance=0f},onDragCancel={distance=0f}) { change,amount ->
                                    change.consume(); distance+=amount.y
                                    if(abs(distance) >= threshold) {
                                        movePlaybackItem(player!!,item.uri,if(distance > 0) 1 else -1); distance=0f
                                    }
                                }
                            }
                        })
                    TextButton(onClick={transport.select(item.uri)},enabled=transport.state.canSelect,modifier=Modifier.weight(1f)) {
                        Text((if(item.uri == transport.state.uri) "▶ " else "")+item.title,maxLines=2,overflow=TextOverflow.Ellipsis)
                    }
                    Column {
                        TextButton(onClick={player?.let { movePlaybackItem(it,item.uri,-1) }},enabled=editable,
                            contentPadding=PaddingValues(0.dp),modifier=Modifier.height(36.dp).width(40.dp).semantics { contentDescription="${item.title}を上へ" }) { Text("↑") }
                        TextButton(onClick={player?.let { movePlaybackItem(it,item.uri,1) }},enabled=editable,
                            contentPadding=PaddingValues(0.dp),modifier=Modifier.height(36.dp).width(40.dp).semantics { contentDescription="${item.title}を下へ" }) { Text("↓") }
                    }
                }
            }
        }
    }},confirmButton={TextButton(onClick=onDismiss) { Text("閉じる") }})
}
