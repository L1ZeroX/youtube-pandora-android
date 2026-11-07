package io.github.l1zerox.pandora

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/** Use exactly the same wallpaper rendering in the application and its preview. */
@Composable
internal fun WallpaperLayer(bitmap: Bitmap?, preferences: Preferences, modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.background)) {
        bitmap?.let {
            Image(it.asImageBitmap(),null,Modifier.fillMaxSize().blur(preferences.blur.dp),
                contentScale=ContentScale.Crop,alpha=1-preferences.opacity)
        }
    }
}

@Composable
internal fun BackgroundPreviewDialog(
    bitmap: Bitmap?, preferences: Preferences, loading: Boolean, imageError: String,
    onChange: (Preferences)->Unit, onPick: ()->Unit, onDismiss: ()->Unit,
) {
    val screenSize = LocalWindowInfo.current.containerSize
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(),color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("背景プレビュー",Modifier.weight(1f),fontSize=22.sp,fontWeight=FontWeight.Bold)
                    TextButton(onClick=onDismiss) { Text("完了") }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // Keep the preview visible while the controls scroll, including in landscape.
                    if(maxWidth > maxHeight) {
                        Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            BackgroundSample(bitmap,preferences,screenSize,Modifier.weight(1f).fillMaxHeight())
                            BackgroundControls(preferences,bitmap != null,loading,imageError,onChange,onPick,
                                Modifier.weight(1f).fillMaxHeight())
                        }
                    } else {
                        Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            BackgroundSample(bitmap,preferences,screenSize,Modifier.weight(1f).fillMaxWidth())
                            BackgroundControls(preferences,bitmap != null,loading,imageError,onChange,onPick,
                                Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundControls(
    p: Preferences, hasImage: Boolean, loading: Boolean, imageError: String,
    onChange: (Preferences)->Unit, onPick: ()->Unit, modifier: Modifier,
) {
    Column(modifier.testTag("background-controls").verticalScroll(rememberScrollState()),
        verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            Button(onClick=onPick,enabled=!loading) { Text(if(loading) "読み込み中…" else "画像を選択") }
            TextButton(onClick={onChange(p.copy(wallpaper=""))},enabled=!loading && p.wallpaper.isNotBlank()) { Text("背景を解除") }
        }
        if(imageError.isNotBlank()) Text(imageError,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
        if(!hasImage && !loading) Text("背景画像を選ぶと仕上がりを確認できます。",fontSize=12.sp)
        Text("背景の透明度 ${(p.opacity*100).roundToInt()}%")
        Slider(p.opacity,{onChange(p.copy(opacity=it))},enabled=hasImage && !loading,
            modifier=Modifier.testTag("background-opacity"))
        Text("パネルの透過率 ${(p.glass*100).roundToInt()}%")
        Slider(p.glass,{onChange(p.copy(glass=it))},valueRange=0f..0.85f,enabled=hasImage && !loading,
            modifier=Modifier.testTag("background-glass"))
        Text("背景のぼかし ${p.blur.roundToInt()}")
        Slider(p.blur,{onChange(p.copy(blur=it))},valueRange=0f..40f,enabled=hasImage && !loading,
            modifier=Modifier.testTag("background-blur"))
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("ダークモード",Modifier.weight(1f))
            Switch(p.dark,{onChange(p.copy(dark=it))})
        }
        Text("変更はプレビューと実際の画面に反映され、自動保存されます。画像は中央を基準に表示します。",
            fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BackgroundSample(bitmap: Bitmap?, p: Preferences, screenSize: IntSize, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("仕上がりプレビュー",fontSize=14.sp,fontWeight=FontWeight.Bold)
        Surface(Modifier.weight(1f).fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=colors.surfaceVariant) {
            // Measure at the actual window size, then scale the whole sample. This keeps
            // center cropping, blur radius, panel opacity and text size in proportion.
            Layout(modifier=Modifier.fillMaxSize().padding(10.dp).testTag("background-preview")
                .clearAndSetSemantics {
                    contentDescription="背景の表示サンプル。透明度 ${(p.opacity*100).roundToInt()}%、パネル透過率 ${(p.glass*100).roundToInt()}%、ぼかし ${p.blur.roundToInt()}"
                },content={
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))) {
                    WallpaperLayer(bitmap,p,Modifier.fillMaxSize())
                    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement=Arrangement.spacedBy(14.dp)) {
                            Text("Youtube Pandora!",fontSize=12.sp,color=colors.onBackground)
                            Text("ダウンロード",fontSize=32.sp,fontWeight=FontWeight.Bold,color=colors.onBackground)
                            Panel(bitmap != null,p.glass) {
                                TintBadge("リンクを追加",strong=true)
                                Surface(Modifier.fillMaxWidth(),color=colors.surfaceVariant,shape=RoundedCornerShape(18.dp)) {
                                    Text("動画のURLを入力",Modifier.padding(16.dp),color=colors.onSurfaceVariant)
                                }
                                PreviewAction("情報を取得")
                            }
                            Panel(bitmap != null,p.glass) {
                                TintBadge("保存する動画",strong=true)
                                Text("動画タイトルのサンプル",fontWeight=FontWeight.Bold)
                                Text("YouTube · 03:24",fontSize=12.sp)
                            }
                            Panel(bitmap != null,p.glass) {
                                TintBadge("保存設定",strong=true)
                                Text("${p.format} · ${if(p.format == "MP3") p.audio else p.quality}")
                            }
                        }
                        Panel(bitmap != null,p.glass) { PreviewAction("ダウンロード") }
                    }
                }
            }) { measurables, constraints ->
                val width = screenSize.width.coerceAtLeast(1)
                val height = screenSize.height.coerceAtLeast(1)
                val sample = measurables.single().measure(Constraints.fixed(width,height))
                val scale = minOf(constraints.maxWidth.toFloat()/width,constraints.maxHeight.toFloat()/height)
                layout(constraints.maxWidth,constraints.maxHeight) {
                    sample.placeWithLayer(((constraints.maxWidth-width*scale)/2).roundToInt(),
                        ((constraints.maxHeight-height*scale)/2).roundToInt()) {
                        scaleX=scale; scaleY=scale; transformOrigin=TransformOrigin(0f,0f)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewAction(text: String) {
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.primary,
        contentColor=MaterialTheme.colorScheme.onPrimary) {
        Text(text,Modifier.padding(14.dp),fontWeight=FontWeight.Medium)
    }
}
