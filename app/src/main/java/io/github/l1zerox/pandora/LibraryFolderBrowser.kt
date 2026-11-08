package io.github.l1zerox.pandora

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun LibraryFolderPath(name: String?, onRoot: ()->Unit, onAll: ()->Unit, onManage: ()->Unit) {
    Column(Modifier.fillMaxWidth().testTag("library-folder-path")) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=onRoot) { Text("フォルダー",maxLines=1) }
            if(name != null) {
                Text("›",Modifier.padding(horizontal=4.dp))
                Text(name,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
            } else Spacer(Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
            TextButton(onClick=onAll) { Text("すべてのファイル") }
            TextButton(onClick=onManage) { Text("フォルダー管理") }
        }
    }
}

@Composable
internal fun LibraryFolderTile(folder: LibraryFolderRow, accent: Color, dark: Boolean, backdrop: LibraryBackdrop, onOpen: ()->Unit) {
    val palette=libraryPalette(accent,dark)
    Surface(onClick=onOpen,shape=RoundedCornerShape(16.dp),color=Color.Transparent,contentColor=palette.content,
        border=BorderStroke(1.dp,palette.content.copy(alpha=.25f)),
        modifier=Modifier.fillMaxWidth().libraryCardGlass(palette,backdrop).testTag("library-folder-${folder.id}")) {
        Row(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("📁",fontSize=26.sp)
            Column(Modifier.weight(1f)) {
                Text(folder.name,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text("${folder.count}件",style=MaterialTheme.typography.bodySmall)
            }
            Text("›",fontSize=24.sp)
        }
    }
}

@Composable
internal fun LibraryFolderCaption(text: String, dark: Boolean, backdrop: LibraryBackdrop) {
    val palette=libraryPalette(null,dark)
    Surface(shape=RoundedCornerShape(12.dp),color=Color.Transparent,contentColor=palette.content,
        modifier=Modifier.fillMaxWidth().libraryCardGlass(palette,backdrop)) {
        Text(text,Modifier.padding(12.dp),style=MaterialTheme.typography.titleSmall)
    }
}
