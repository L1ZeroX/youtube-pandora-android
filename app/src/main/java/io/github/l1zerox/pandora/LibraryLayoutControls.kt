package io.github.l1zerox.pandora

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

@Composable
internal fun LibraryLayoutButton(layout: LibraryLayout, accent: Color, onOpen: ()->Unit, onSelect: (LibraryLayout)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedIconButton(onClick={onOpen(); expanded=true},modifier=Modifier.size(48.dp).semantics {
            contentDescription="レイアウトを変更"; stateDescription=layout.label
        },colors=IconButtonDefaults.outlinedIconButtonColors(containerColor=accent,contentColor=Color.White)) {
            LibraryLayoutIcon(layout)
        }
        DropdownMenu(expanded,onDismissRequest={expanded=false}) {
            LibraryLayout.entries.forEach { option ->
                DropdownMenuItem(text={Text(option.label)},leadingIcon={LibraryLayoutIcon(option)},
                    trailingIcon={if(option == layout) Text("✓")},onClick={expanded=false; onSelect(option)})
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryLayoutChoices(layout: LibraryLayout, onSelect: (LibraryLayout)->Unit) {
    Text("レイアウト")
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        LibraryLayout.entries.forEach { option ->
            FilterChip(selected=layout == option,onClick={onSelect(option)},label={Text(option.label)},
                leadingIcon={LibraryLayoutIcon(option)})
        }
    }
}

@Composable
private fun LibraryLayoutIcon(layout: LibraryLayout) {
    val color=LocalContentColor.current
    Canvas(Modifier.size(22.dp)) {
        val columns=layout.columns
        val rows=when(layout) { LibraryLayout.COMPACT -> 4; LibraryLayout.LARGE -> 1; else -> 2 }
        val gap=3.dp.toPx()
        val width=(size.width-gap*(columns-1))/columns
        val height=(size.height-gap*(rows-1))/rows
        for(row in 0 until rows) for(column in 0 until columns) {
            drawRoundRect(color,Offset(column*(width+gap),row*(height+gap)),Size(width,height),CornerRadius(1.dp.toPx()))
        }
    }
}
