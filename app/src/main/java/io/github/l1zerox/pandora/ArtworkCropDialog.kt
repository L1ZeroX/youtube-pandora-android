package io.github.l1zerox.pandora

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

@Composable
internal fun ArtworkCropDialog(bitmap: Bitmap?, loading: Boolean, saving: Boolean, busy: Boolean, error: String,
    onDismiss: ()->Unit, onSave: (ArtworkCrop)->Unit) {
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var centerX by rememberSaveable { mutableFloatStateOf(.5f) }
    var centerY by rememberSaveable { mutableFloatStateOf(.5f) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val crop=ArtworkCrop(zoom,centerX,centerY)
    fun change(next: ArtworkCrop) { zoom=next.zoom; centerX=next.centerX; centerY=next.centerY }
    val enabled=bitmap != null && !loading && !saving && !busy
    val accent=MaterialTheme.colorScheme.primary
    val image=remember(bitmap) { bitmap?.asImageBitmap() }
    val preview: @Composable (Modifier)->Unit = { modifier ->
        BoxWithConstraints(modifier,contentAlignment=Alignment.Center) {
            val side=minOf(maxWidth,maxHeight)
            Canvas(Modifier.size(side).background(Color.White).border(3.dp,accent)
                .testTag("artwork-crop-preview").semantics { contentDescription="アルバムアートの正方形トリミング範囲" }
                .onSizeChanged { viewport=it }.pointerInput(bitmap,enabled) {
                    if(enabled && bitmap != null) detectTransformGestures { centroid,pan,scale,_ ->
                        if(viewport.width > 0 && viewport.height > 0) change(transformArtworkCrop(
                            ArtworkCrop(zoom,centerX,centerY),bitmap.width,bitmap.height,
                            pan.x/viewport.width,pan.y/viewport.height,scale,centroid.x/viewport.width,centroid.y/viewport.height))
                    }
                }) {
                if(bitmap != null && image != null) {
                    val bounds=artworkCropBounds(crop,bitmap.width,bitmap.height)
                    drawImage(image,srcOffset=IntOffset(bounds.left,bounds.top),srcSize=IntSize(bounds.size,bounds.size),
                        dstSize=IntSize(size.width.roundToInt(),size.height.roundToInt()))
                    for(part in 1..2) {
                        val x=size.width*part/3f; val y=size.height*part/3f
                        drawLine(Color.White.copy(alpha=.65f),Offset(x,0f),Offset(x,size.height),1.dp.toPx())
                        drawLine(Color.White.copy(alpha=.65f),Offset(0f,y),Offset(size.width,y),1.dp.toPx())
                    }
                }
            }
            if(loading) CircularProgressIndicator()
        }
    }
    val controls: @Composable (Modifier)->Unit = { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("枠の内側を正方形で保存します。画像をドラッグして位置を調整し、ピンチで拡大できます。",fontSize=13.sp)
            Text("拡大 ${"%.1f".format(zoom)}倍",fontWeight=FontWeight.Bold)
            Slider(zoom,{ next -> bitmap?.let { change(boundedCrop(crop.copy(zoom=next),it.width,it.height)) } },
                enabled=enabled,valueRange=1f..5f,modifier=Modifier.testTag("artwork-crop-zoom").semantics { contentDescription="画像の拡大率" })
            TextButton(onClick={change(ArtworkCrop())},enabled=enabled) { Text("位置・拡大をリセット") }
            if(busy) Text("DL中・一時停止中は変更できません。",color=MaterialTheme.colorScheme.error)
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error,fontSize=13.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick=onDismiss,enabled=!saving,modifier=Modifier.weight(1f)) { Text("キャンセル") }
                Button(onClick={onSave(crop)},enabled=enabled,modifier=Modifier.weight(1f)) { Text(if(saving) "保存中…" else "この範囲を使う") }
            }
        }
    }
    Dialog(onDismissRequest={if(!saving) onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("アルバムアートをトリミング",fontSize=21.sp,fontWeight=FontWeight.Bold)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val controlHeight=maxHeight*.55f
                    if(maxWidth > maxHeight) Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        preview(Modifier.weight(1f).fillMaxHeight())
                        controls(Modifier.weight(1f).fillMaxHeight())
                    } else Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        preview(Modifier.weight(1f).fillMaxWidth())
                        controls(Modifier.fillMaxWidth().heightIn(max=controlHeight))
                    }
                }
            }
        }
    }
}
