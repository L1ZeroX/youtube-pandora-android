package io.github.l1zerox.pandora

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared with the appearance preview; transport and deletion actions remain owned by the screen. */
@Composable
internal fun LibraryCardBody(entry: LibraryEntry, artwork: Bitmap?, palette: LibraryPalette, layout: LibraryLayout,
    play: @Composable ()->Unit, menu: @Composable ()->Unit, footer: @Composable ()->Unit = {}) {
    val compact=layout == LibraryLayout.COMPACT
    val three=layout == LibraryLayout.GRID_THREE
    val padding=when { three -> 6.dp; compact -> 8.dp; layout == LibraryLayout.GRID_TWO -> 10.dp; else -> 12.dp }
    Column(Modifier.padding(padding)) {
        if(layout == LibraryLayout.STANDARD || compact) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(if(compact) 8.dp else 12.dp)) {
                LibraryCardArtwork(entry,artwork,palette,Modifier.size(if(compact) 48.dp else 80.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.title,maxLines=if(compact) 1 else 2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold,
                        color=palette.content,fontSize=if(compact) 14.sp else 16.sp)
                    LibraryCardInfo(entry,palette,false)
                    LibraryDubbingLabel(entry,palette)
                }
                if(compact) Row(verticalAlignment=Alignment.CenterVertically) { play(); menu() }
                else Column(horizontalAlignment=Alignment.CenterHorizontally) { play(); menu() }
            }
        } else {
            Box(Modifier.fillMaxWidth()) {
                LibraryCardArtwork(entry,artwork,palette,Modifier.fillMaxWidth().aspectRatio(
                    if(layout == LibraryLayout.LARGE && entry.video) 16f/9f else 1f))
                Surface(modifier=Modifier.align(Alignment.TopEnd),shape=RoundedCornerShape(12.dp),
                    color=palette.surface,contentColor=palette.content) { menu() }
            }
            Spacer(Modifier.height(8.dp))
            Text(entry.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold,color=palette.content,
                fontSize=when { three -> 12.sp; layout == LibraryLayout.LARGE -> 18.sp; else -> 14.sp })
            LibraryDubbingLabel(entry,palette)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { LibraryCardInfo(entry,palette,three) }
                play()
            }
        }
        footer()
    }
}

@Composable
private fun LibraryCardArtwork(entry: LibraryEntry, artwork: Bitmap?, palette: LibraryPalette, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center) {
        if(artwork != null) Image(artwork.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Text(if(entry.video) "▶" else "♪",fontSize=32.sp,color=palette.content)
    }
}

@Composable
private fun LibraryCardInfo(entry: LibraryEntry, palette: LibraryPalette, stacked: Boolean) {
    val duration=if(entry.durationMs > 0) durationText(entry.durationMs/1000.0) else ""
    Text(entry.category+if(duration.isEmpty()) "" else if(stacked) "\n$duration" else " · $duration",
        fontSize=if(stacked) 11.sp else 12.sp,color=palette.detail)
}

@Composable
private fun LibraryDubbingLabel(entry: LibraryEntry, palette: LibraryPalette) {
    entry.dubbingLabel?.let { Text(it,fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=palette.content,
        modifier=Modifier.padding(top=4.dp)) }
}
