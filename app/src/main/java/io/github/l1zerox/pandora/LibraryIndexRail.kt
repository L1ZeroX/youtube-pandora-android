package io.github.l1zerox.pandora

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Scrolling reveals a translucent index; dragging the edge makes it fully visible and selects a letter. */
@Composable
internal fun LibraryIndexRail(targets: Map<String,Int>, onStart: ()->Unit, onJump: (String?)->Unit, scrolling: Boolean = false) {
    val labels=targets.keys.toList()
    var selected by remember { mutableStateOf<String?>(null) }
    var height by remember { mutableIntStateOf(0) }
    val start by rememberUpdatedState(onStart)
    val jump by rememberUpdatedState(onJump)
    if(labels.isEmpty()) return
    var recent by remember { mutableStateOf(false) }
    LaunchedEffect(scrolling,selected != null) {
        if(scrolling || selected != null) recent=true
        else { delay(1000); recent=false }
    }
    val opacity by animateFloatAsState(if(selected != null) 1f else if(scrolling || recent) .5f else 0f,
        animationSpec=tween(180),label="library-index-opacity")
    Box(Modifier.fillMaxSize()) {
        selected?.let { label ->
            Surface(Modifier.align(Alignment.CenterEnd).padding(end=34.dp).size(72.dp).testTag("library-index-preview"),
                shape=RoundedCornerShape(20.dp),color=Color(0xE6222222),contentColor=Color.White) {
                Box(contentAlignment=Alignment.Center) { Text(label,fontSize=34.sp) }
            }
        }
        Column(Modifier.align(Alignment.CenterEnd).width(24.dp).fillMaxHeight().onSizeChanged { height=it.height }
            .graphicsLayer { alpha=opacity }
            .background(Color(0xB8000000),RoundedCornerShape(12.dp)).testTag("library-index-rail")
            .semantics { contentDescription="文字索引。右端をドラッグして移動" }
            .pointerInput(labels) {
                fun select(y: Float) {
                    val label=libraryIndexAt(labels,y,height) ?: return
                    selected=label; jump(label)
                }
                detectVerticalDragGestures(onDragStart={start(); select(it.y)},
                    onDragEnd={selected=null; jump(null)},onDragCancel={selected=null; jump(null)},
                    onVerticalDrag={change,_ -> change.consume(); select(change.position.y)})
            },verticalArrangement=Arrangement.SpaceEvenly,horizontalAlignment=Alignment.CenterHorizontally) {
            if(opacity > 0) labels.forEach { Text(it,fontSize=11.sp,color=Color.White) }
        }
    }
}
