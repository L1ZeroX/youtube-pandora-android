package io.github.l1zerox.pandora

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.conflate

private data class StoryboardResult(val loading: Boolean = true,val source: SaveRangeStoryboard? = null)
private data class PreviewFrame(val loading: Boolean = true,val image: Bitmap? = null)

@Composable
internal fun SaveRangeFramePreviews(video: Video, selection: SaveRangeBarSelection, loader: RangePreviewLoader? = null) = key(video.key,video.url) {
    val context=LocalContext.current
    val owner=loader ?: remember(context) { NetworkRangePreviewLoader(context) }
    var retry by remember { mutableIntStateOf(0) }
    fun reload() { owner.invalidate(video); retry++ }
    val result by key(retry,owner) { produceState(StoryboardResult()) {
        value=try { StoryboardResult(false,owner.storyboard(video)) }
        catch(_: TimeoutCancellationException) { StoryboardResult(false) }
        catch(e: CancellationException) { throw e }
        catch(_: Exception) { StoryboardResult(false) }
    } }
    val source=result.source
    if(source != null) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            RangeFrame("開始",source.tile(selection.start),owner,retry,Modifier.weight(1f))
            RangeFrame("終了",source.tile(selection.end,true),owner,retry,Modifier.weight(1f))
        }
        Text("プレビュー画像は一定間隔です。保存時刻はバー・入力欄の指定を使います。",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={reload()},contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)) { Text("画像を再読み込み",fontSize=12.sp) }
    } else if(result.loading) {
        Row(Modifier.fillMaxWidth().height(76.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
            Text("位置のプレビューを取得しています…",fontSize=12.sp)
        }
    } else {
        Text("この動画の位置プレビューを取得できません。範囲指定は使えます。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={reload()}) { Text("プレビューを再取得") }
    }
}

@Composable
private fun RangeFrame(label: String,tile: StoryboardTile,loader: RangePreviewLoader,retry: Int,modifier: Modifier) {
    val requested by rememberUpdatedState(tile)
    val result by key(loader,retry) { produceState(PreviewFrame()) {
        // Finish the in-flight sheet request while dragging; then render only the latest queued cell.
        // The outer video key still isolates every image from the previous reference video.
        snapshotFlow { requested }.conflate().collect { target ->
            value=value.copy(loading=true)
            value=try { PreviewFrame(target != requested,loader.frame(target)) }
            catch(e: CancellationException) { throw e }
            catch(_: Exception) { PreviewFrame(false) }
        }
    } }
    Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f/9).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).testTag("save-range-preview-$label"),contentAlignment=Alignment.Center) {
            val image=result.image
            if(image != null) Image(image.asImageBitmap(),"${label}位置のプレビュー",Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
            else if(!result.loading) Text("画像なし",fontSize=12.sp)
            if(result.loading) CircularProgressIndicator(Modifier.size(18.dp).align(if(image == null) Alignment.Center else Alignment.TopEnd)
                .padding(2.dp),strokeWidth=2.dp)
        }
    }
}
