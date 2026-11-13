package io.github.l1zerox.pandora

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The edge remains a small drag target; letters and the large preview appear only during dragging. */
@Composable
internal fun LibraryIndexRail(targets: Map<String,Int>, onStart: ()->Unit, onJump: (String?)->Unit) {
    val labels=targets.keys.toList()
    var selected by remember { mutableStateOf<String?>(null) }
    var height by remember { mutableIntStateOf(0) }
    val start by rememberUpdatedState(onStart)
    val jump by rememberUpdatedState(onJump)
    if(labels.isEmpty()) return
    Box(Modifier.fillMaxSize()) {
        selected?.let { label ->
            Surface(Modifier.align(Alignment.CenterEnd).padding(end=34.dp).size(72.dp),
                shape=RoundedCornerShape(20.dp),color=Color(0xE6222222),contentColor=Color.White) {
                Box(contentAlignment=Alignment.Center) { Text(label,fontSize=34.sp) }
            }
        }
        Column(Modifier.align(Alignment.CenterEnd).width(24.dp).fillMaxHeight().onSizeChanged { height=it.height }
            .background(if(selected != null) Color(0xB8000000) else Color.Transparent,RoundedCornerShape(12.dp))
            .semantics { contentDescription="文字索引。右端をドラッグすると表示" }
            .pointerInput(labels) {
                fun select(y: Float) {
                    val label=libraryIndexAt(labels,y,height) ?: return
                    selected=label; jump(label)
                }
                detectVerticalDragGestures(onDragStart={start(); select(it.y)},
                    onDragEnd={selected=null; jump(null)},onDragCancel={selected=null; jump(null)},
                    onVerticalDrag={change,_ -> change.consume(); select(change.position.y)})
            },verticalArrangement=Arrangement.SpaceEvenly,horizontalAlignment=Alignment.CenterHorizontally) {
            if(selected != null) labels.forEach { Text(it,fontSize=11.sp,color=Color.White) }
        }
    }
}
