package io.github.l1zerox.pandora

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun LibraryAppearanceDialog(wallpaper: Bitmap?, preferences: Preferences, entry: LibraryEntry?, accent: Color, previewColors: ColorScheme,
    onChange: (Preferences)->Unit, onDismiss: ()->Unit, previewEntries: List<LibraryEntry> = emptyList()) {
    val context=LocalContext.current
    val samples=remember(entry,previewEntries) { (listOfNotNull(entry)+previewEntries).distinctBy { it.uri }.take(6) }
    val artwork by produceState<Map<String,Bitmap>>(emptyMap(),samples.map { it.uri }) {
        value=emptyMap()
        value=buildMap { for(item in samples) LibraryRepository.artwork(context,item)?.let { put(item.uri,it) } }
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
                                LibraryAppearanceSample(wallpaper,artwork,samples,preferences,previewColors,Modifier.weight(1f).fillMaxHeight())
                                LibraryAppearanceMenu(preferences,onChange,Modifier.weight(1f).fillMaxHeight())
                            }
                        } else {
                            Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                LibraryAppearanceSample(wallpaper,artwork,samples,preferences,previewColors,Modifier.weight(1f).fillMaxWidth())
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
        Text("再生パネル",fontWeight=FontWeight.Bold)
        LibraryPlayerAppearanceControls(preferences,onChange)
        LibraryLayoutChoices(LibraryLayout.fromId(preferences.libraryLayout)) { onChange(preferences.copy(libraryLayout=it.id)) }
        Text("動画・音楽パネル",fontWeight=FontWeight.Bold)
        Text("サムネイルの色を保ったまま、透過とぼかしを調整します。",fontSize=13.sp)
        LibraryAppearanceControls(preferences,onChange)
        Text("背景画像はダウンロード画面と共通です。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LibraryAppearanceSample(wallpaper: Bitmap?, artwork: Map<String,Bitmap>, entries: List<LibraryEntry>, p: Preferences, previewColors: ColorScheme, modifier: Modifier) {
    val layout=LibraryLayout.fromId(p.libraryLayout)
    val samples=entries.ifEmpty { listOf(
        LibraryEntry("preview-video","動画タイトルのサンプル","video/mp4",204_000),
        LibraryEntry("preview-music","音楽タイトルのサンプル","audio/mpeg",242_000),
        LibraryEntry("preview-short","ショートのサンプル","video/mp4",30_000,width=1080,height=1920)) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val frosted by produceState<Bitmap?>(wallpaper,wallpaper,p.libraryBlur) {
        val strength=p.libraryBlur.coerceIn(0,20)
        value=if(wallpaper == null || strength == 0) wallpaper
            else withContext(Dispatchers.Default) { blurThumbnail(wallpaper,maxEdge=80,passes=strength) }
    }
    val backdrop=remember(frosted,bounds,p.opacity,p.dark,p.libraryGlass) {
        LibraryBackdrop(frosted,bounds,p.opacity,appBackgroundColor(p.dark),p.libraryGlass)
    }
    Column(modifier,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("仕上がりプレビュー",fontSize=14.sp,fontWeight=FontWeight.Bold)
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).onGloballyPositioned {
            val position=it.positionInWindow()
            bounds=Rect(position.x,position.y,position.x+it.size.width,position.y+it.size.height)
        }.testTag("library-appearance-preview").clearAndSetSemantics {
            contentDescription="ライブラリーの表示サンプル。パネル透過率 ${(libraryPanelTransparency(p.libraryGlass)*100).roundToInt()}%、ぼかし ${p.libraryBlur.coerceIn(0,20)}"
            stateDescription=layout.label
        }) {
            WallpaperLayer(wallpaper,p,Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                val headerTint=readableGlassTint(appSurfaceColor(p.dark)).let { it.copy(alpha=maxOf(it.alpha,1-p.glass)) }
                Surface(shape=RoundedCornerShape(16.dp),color=if(wallpaper != null) headerTint else previewColors.surface,
                    contentColor=if(wallpaper != null) Color.White else previewColors.onSurface) {
                    Text("ライブラリー",Modifier.fillMaxWidth().padding(12.dp),fontSize=20.sp,fontWeight=FontWeight.Bold,
                        textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                }
                if(p.libraryPlayerVisible) {
                    val sample=samples.first()
                    val image=artwork[sample.uri]
                    val source=remember(image) { image?.let { libraryThumbnailColor(it) } }
                    LibraryPlayerPanel(LibraryPlayerState(uri=sample.uri,title=sample.title,artist="再生パネルのプレビュー",
                        playing=true,position=sample.durationMs/3,duration=sample.durationMs,seekable=true,canPlay=true,canPrevious=true,canNext=true,advancing=true),
                        image,source,p.libraryPlayerScale,onOpen={},onToggle={},onPrevious={},onNext={},onSeek={},waveform=p.libraryPlayerWave)
                }
                for(row in samples.take(if(layout.columns > 1) 6 else 2).chunked(layout.columns)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        row.forEach { entry ->
                            val image=artwork[entry.uri]
                            val source=remember(image) { image?.let { libraryThumbnailColor(it) } }
                            val palette=remember(source,p.dark) { libraryPalette(source,p.dark) }
                            Surface(shape=RoundedCornerShape(16.dp),color=Color.Transparent,contentColor=palette.content,
                                border=BorderStroke(1.dp,palette.content.copy(alpha=.25f)),
                                modifier=Modifier.weight(1f).libraryCardGlass(palette,backdrop)) {
                                LibraryCardBody(entry,image,palette,layout,
                                    play={Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) { Text("▶",fontSize=22.sp) }},
                                    menu={Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) { Text("⋮",fontSize=22.sp) }})
                            }
                        }
                        repeat(layout.columns-row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
