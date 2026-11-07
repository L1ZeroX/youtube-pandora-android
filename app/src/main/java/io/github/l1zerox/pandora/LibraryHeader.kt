package io.github.l1zerox.pandora

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun LibraryHeader(accent: Color, loading: Boolean, onBack: ()->Unit, onRefresh: ()->Unit) {
    val buttons=ButtonDefaults.buttonColors(containerColor=accent,contentColor=Color.White)
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("library-header")) {
        val titleSize=if(maxWidth < 300.dp) 20.sp else 24.sp
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            Button(onClick=onBack,modifier=Modifier.width(64.dp).testTag("library-back"),colors=buttons,
                contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp)) { Text("戻る") }
            Text("ライブラリー",Modifier.weight(1f).testTag("library-heading"),fontSize=titleSize,
                fontWeight=FontWeight.Bold,textAlign=TextAlign.Center,maxLines=1,overflow=TextOverflow.Ellipsis)
            Button(onClick=onRefresh,enabled=!loading,modifier=Modifier.width(64.dp).testTag("library-refresh"),colors=buttons,
                contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp)) { Text("更新") }
        }
    }
}
