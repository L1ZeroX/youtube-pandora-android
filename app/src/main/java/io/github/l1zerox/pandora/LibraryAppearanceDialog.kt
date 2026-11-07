package io.github.l1zerox.pandora

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun LibraryAppearanceDialog(wallpaper: Bitmap?, preferences: Preferences, entry: LibraryEntry?, accent: Color, previewColors: ColorScheme,
    onChange: (Preferences)->Unit, onDismiss: ()->Unit) {
    val context=LocalContext.current
    val artwork by produceState<Bitmap?>(null,entry?.uri) {
        value=null
        if(entry != null) value=LibraryRepository.artwork(context,entry)
    }
    val colors=if(preferences.dark) darkColorScheme(primary=accent,onPrimary=Color.White,
        background=appBackgroundColor(true),surface=appSurfaceColor(true))
        else lightColorScheme(primary=accent,onPrimary=Color.White,
            background=appBackgroundColor(false),surface=appSurfaceColor(false))
    MaterialTheme(colorScheme=colors) {
        Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
            Surface(Modifier.fillMaxSize().safeDrawingPadding(),color=colors.surface) {
                Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("ライブラリーの表示調整",Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.Bold)
                        Button(onClick=onDismiss) { Text("完了") }
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        if(maxWidth > maxHeight) {
                            Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                                LibraryAppearanceSample(wallpaper,artwork,entry,preferences,previewColors,Modifier.weight(1f).fillMaxHeight())
                                LibraryAppearanceMenu(preferences,onChange,Modifier.weight(1f).fillMaxHeight())
                            }
                        } else {
                            Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                LibraryAppearanceSample(wallpaper,artwork,entry,preferences,previewColors,Modifier.weight(1f).fillMaxWidth())
                                LibraryAppearanceMenu(preferences,onChange,Modifier.weight(1f).fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryAppearanceMenu(preferences: Preferences, onChange: (Preferences)->Unit, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("動画・音楽パネル",fontWeight=FontWeight.Bold)
        Text("サムネイルの色を保ったまま、透過とぼかしを調整します。",fontSize=13.sp)
        LibraryAppearanceControls(preferences,onChange)
        Text("背景画像はダウンロード画面と共通です。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LibraryAppearanceSample(wallpaper: Bitmap?, artwork: Bitmap?, entry: LibraryEntry?, p: Preferences, previewColors: ColorScheme, modifier: Modifier) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val frosted by produceState<Bitmap?>(wallpaper,wallpaper,p.libraryBlur) {
        val strength=p.libraryBlur.coerceIn(0,20)
        value=if(wallpaper == null || strength == 0) wallpaper
            else withContext(Dispatchers.Default) { blurThumbnail(wallpaper,maxEdge=80,passes=strength) }
    }
    val backdrop=remember(frosted,bounds,p.opacity,p.dark,p.libraryGlass) {
        LibraryBackdrop(frosted,bounds,p.opacity,appBackgroundColor(p.dark),p.libraryGlass)
    }
    val source=remember(artwork) { artwork?.let { libraryThumbnailColor(it) } }
    val palette=remember(source,p.dark) { libraryPalette(source,p.dark) }
    Column(modifier,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("仕上がりプレビュー",fontSize=14.sp,fontWeight=FontWeight.Bold)
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).onGloballyPositioned {
            val position=it.positionInWindow()
            bounds=Rect(position.x,position.y,position.x+it.size.width,position.y+it.size.height)
        }.testTag("library-appearance-preview").clearAndSetSemantics {
            contentDescription="ライブラリーの表示サンプル。パネル透過率 ${(libraryPanelTransparency(p.libraryGlass)*100).roundToInt()}%、ぼかし ${p.libraryBlur.coerceIn(0,20)}"
        }) {
            WallpaperLayer(wallpaper,p,Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                val headerTint=readableGlassTint(appSurfaceColor(p.dark)).let { it.copy(alpha=maxOf(it.alpha,1-p.glass)) }
                Surface(shape=RoundedCornerShape(16.dp),color=if(wallpaper != null) headerTint else previewColors.surface,
                    contentColor=if(wallpaper != null) Color.White else previewColors.onSurface) {
                    Text("ライブラリー",Modifier.fillMaxWidth().padding(12.dp),fontSize=20.sp,fontWeight=FontWeight.Bold,
                        textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                }
                Surface(shape=RoundedCornerShape(16.dp),color=Color.Transparent,contentColor=palette.content,
                    border=BorderStroke(1.dp,palette.content.copy(alpha=.25f)),
                    modifier=Modifier.fillMaxWidth().libraryCardGlass(palette,backdrop)) {
                    Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(80.dp).clip(RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center) {
                            if(artwork != null) Image(artwork.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                            else Text(if(entry?.video != false) "▶" else "♪",fontSize=32.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(entry?.title ?: "動画タイトルのサンプル",fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text((entry?.category ?: "動画")+" · "+durationText((entry?.durationMs?.takeIf { it > 0 } ?: 204_000)/1000.0),
                                fontSize=12.sp,color=palette.detail)
                            entry?.dubbingLabel?.let { Text(it,fontSize=12.sp) }
                        }
                        Text("▶",fontSize=22.sp)
                    }
                }
            }
        }
    }
}
