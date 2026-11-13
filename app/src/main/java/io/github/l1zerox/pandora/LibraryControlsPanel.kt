package io.github.l1zerox.pandora

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pull at the top to expand; scrolling down the list immediately collapses the tools. */
@Composable
internal fun rememberLibraryControlsPull(expanded: Boolean, atTop: ()->Boolean, onExpand: ()->Unit,
    onCollapse: ()->Unit = {}): NestedScrollConnection {
    val open by rememberUpdatedState(expanded)
    val isAtTop by rememberUpdatedState(atTop)
    val expand by rememberUpdatedState(onExpand)
    val collapse by rememberUpdatedState(onCollapse)
    val threshold=with(LocalDensity.current) { 64.dp.toPx() }
    return remember(threshold) {
        object: NestedScrollConnection {
            val gesture=LibraryControlsScroll(threshold)
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val pulling=!open && isAtTop() && source == NestedScrollSource.UserInput && available.y > 0
                when(gesture.scroll(open,isAtTop(),consumed.y,available.y,source == NestedScrollSource.UserInput)) {
                    true -> expand(); false -> collapse(); null -> Unit
                }
                if(pulling) return Offset(0f,available.y)
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                gesture.endGesture()
                return Velocity.Zero
            }
        }
    }
}

@Composable
internal fun LibraryBrowseButtons(searchExpanded: Boolean, onFolder: ()->Unit, onSearch: ()->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick=onFolder,modifier=Modifier.weight(1f).testTag("library-folder-button"),contentPadding=PaddingValues(horizontal=8.dp)) {
            Text("📁 フォルダー",maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        OutlinedButton(onClick=onSearch,modifier=Modifier.weight(1f).testTag("library-search-button"),contentPadding=PaddingValues(horizontal=8.dp)) {
            Text(if(searchExpanded) "検索を閉じる" else "検索 🔎",maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun LibraryControlsPanel(expanded: Boolean, summary: String, onExpandedChange: (Boolean)->Unit,
    content: @Composable ColumnScope.()->Unit) {
    val threshold=with(LocalDensity.current) { 32.dp.toPx() }
    val change by rememberUpdatedState(onExpandedChange)
    val foreground=LocalContentColor.current
    Column {
        TextButton(onClick={change(!expanded)},contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp),
            colors=ButtonDefaults.textButtonColors(contentColor=foreground),
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("library-controls-toggle")
                .semantics { stateDescription=if(expanded) "展開中" else "折りたたみ中" }
                .pointerInput(expanded,threshold) {
                    var distance=0f
                    detectVerticalDragGestures(onDragStart={distance=0f},onVerticalDrag={event,dy ->
                        event.consume(); distance+=dy
                        if(!expanded && distance >= threshold) change(true)
                        else if(expanded && distance <= -threshold) change(false)
                    })
                }) {
            Text(if(expanded) "操作を隠す" else "操作を表示",fontSize=13.sp)
            Text(summary,Modifier.weight(1f).padding(horizontal=8.dp),fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            Canvas(Modifier.size(16.dp)) {
                val middle=if(expanded) size.height*.35f else size.height*.65f
                val edge=size.height-middle
                drawLine(foreground,Offset(size.width*.2f,edge),Offset(size.width*.5f,middle),2.dp.toPx(),StrokeCap.Round)
                drawLine(foreground,Offset(size.width*.5f,middle),Offset(size.width*.8f,edge),2.dp.toPx(),StrokeCap.Round)
            }
        }
        AnimatedVisibility(expanded,enter=expandVertically(expandFrom=Alignment.Top)+fadeIn(),
            exit=shrinkVertically(shrinkTowards=Alignment.Top)+fadeOut()) {
            Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*.45f).dp)
                .verticalScroll(rememberScrollState()).testTag("library-controls-content"),content=content)
        }
    }
}
