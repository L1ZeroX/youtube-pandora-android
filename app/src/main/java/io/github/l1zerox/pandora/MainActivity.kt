package io.github.l1zerox.pandora

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil.request.ImageRequest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); BubbleRuntime.foreground.value=true; enableEdgeToEdge()
        if(savedInstanceState == null) acceptShare(intent)
        setContent { PandoraScreen() }
        AppUpdates.check()
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); acceptShare(intent) }
    override fun onStart() { super.onStart(); BubbleRuntime.foreground.value=true }
    override fun onResume() { super.onResume(); BubbleRuntime.sync(this) }
    override fun onStop() { super.onStop(); if(!isChangingConfigurations) BubbleRuntime.foreground.value=false }
    private fun acceptShare(intent: Intent?) {
        if(intent?.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            val count=Store.receiveShare(text)
            Toast.makeText(this,if(count > 0) "$count 件をDLキューに追加しました。" else "追加できる新しいURLはありません。",Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun PandoraScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Store.state.collectAsStateWithLifecycle()
    val p = s.preferences
    var text by rememberSaveable { mutableStateOf("") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var backgroundSettings by rememberSaveable { mutableStateOf(false) }
    var logs by rememberSaveable { mutableStateOf(false) }
    val logSnapshot by AppLog.state.collectAsStateWithLifecycle()
    val updateState by AppUpdates.state.collectAsStateWithLifecycle()
    var preview by remember { mutableStateOf<Video?>(null) }
    var error by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    fun pasteLinks() {
        if(s.busy) return
        runCatching {
            val copied = clipboard.getText()?.text
            require(!copied.isNullOrBlank()) { "クリップボードにテキストがありません。リンクをコピーしてからお試しください。" }
            text = appendClipboardText(text,copied)
        }.onFailure { error = it.message ?: "クリップボードを読み取れませんでした。" }
    }
    var pending by remember { mutableStateOf<Intent?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending?.let { task -> runCatching { ContextCompat.startForegroundService(context,task) }.onFailure { error = it.message.orEmpty(); AppLog.error("処理開始",it) } }; pending = null
    }
    fun start(action: String, retry: Boolean = false) {
        if(s.busy) return
        try {
            if(action == "FETCH") require(validUrls(text).isNotEmpty()) { "URLを入力してください。" }
            val intent = Intent(context,DownloadService::class.java).setAction(action).putExtra("urls",text).putExtra("retry",retry)
            if(Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                pending = intent; permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else ContextCompat.startForegroundService(context,intent)
        } catch(e: Exception) { error = e.message ?: "開始できませんでした。"; AppLog.error("処理開始",e) }
    }
    LaunchedEffect(s.incomingUrl) { if(s.incomingUrl.isNotEmpty()) { text = s.incomingUrl; Store.state.update { it.copy(incomingUrl="") } } }
    val bitmap by produceState<Bitmap?>(null,p.wallpaper) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(p.wallpaper)
                if(!file.isFile) null else ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    val scale = (max(info.size.width,info.size.height) / 1600f).coerceAtLeast(1f)
                    decoder.setTargetSize((info.size.width/scale).toInt(),(info.size.height/scale).toInt())
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }.getOrNull()
        }
    }
    val accent = remember(bitmap,p.dark) { readableBadgeColor(bitmap?.let { accentColor(it,p.dark) } ?: Color(0xFF4388FF)) }
    val animatedAccent by animateColorAsState(accent,label="accent")
    val colors = if(p.dark) darkColorScheme(primary=animatedAccent,onPrimary=Color.White,
        background=Color(0xFF090B10),surface=Color(0xFF191C23)) else lightColorScheme(primary=animatedAccent,onPrimary=Color.White,
        background=Color(0xFFF3F4F6),surface=Color.White)
    MaterialTheme(colorScheme=colors) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            WallpaperLayer(bitmap,p,Modifier.fillMaxSize())
            Scaffold(containerColor=Color.Transparent, contentColor=colors.onBackground, bottomBar={
                Surface(color=colors.surface.copy(alpha=if(bitmap == null) 1f else 1-p.glass),contentColor=colors.onSurface,shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp)) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(s.message,modifier=Modifier.weight(1f),maxLines=3,overflow=TextOverflow.Ellipsis,fontSize=13.sp)
                            TextButton(onClick={logs=true}) { Text(if(logSnapshot.errors > 0) "ログ (${logSnapshot.errors})" else "ログを見る") }
                        }
                        if(s.busy) {
                            // Drawing/semantics may invoke this callback after the next state update,
                            // before recomposition replaces it. Capture the value, not the live state.
                            val displayedProgress=s.progress?.takeIf { it.isFinite() }?.coerceIn(0f,1f)
                            val progressModifier=Modifier.fillMaxWidth().testTag("download-progress")
                            if(displayedProgress == null) LinearProgressIndicator(progressModifier)
                            else LinearProgressIndicator(progress={displayedProgress},modifier=progressModifier)
                            Text("${s.done} / ${s.total} 件" + (displayedProgress?.let { " · ${(it*100).toInt()}%" } ?: ""),fontSize=12.sp)
                            if(s.detail.isNotBlank()) Text(s.detail,maxLines=2,fontSize=10.sp,color=colors.onSurfaceVariant)
                        }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick={start("DOWNLOAD",true)},enabled=!s.busy && s.videos.any { it.status == "失敗" }) { Text("再試行") }
                            if(s.busy) {
                                if(BuildConfig.BETA && s.operation == "DOWNLOAD") Button(
                                    onClick={context.startService(Intent(context,DownloadService::class.java).setAction(if(s.paused) "RESUME" else "PAUSE"))},
                                    enabled=!s.stopping,modifier=Modifier.weight(1f)) { Text(if(s.paused) "再開" else "一時停止") }
                                Button(onClick={context.startService(Intent(context,DownloadService::class.java).setAction("STOP"))},
                                    enabled=!s.stopping,modifier=Modifier.weight(1f)) { Text(if(BuildConfig.BETA) "キャンセル" else "停止") }
                            }
                            else Button(onClick={start("DOWNLOAD")},enabled=s.videos.any { it.selected && it.status != "完了" },modifier=Modifier.weight(1f).height(48.dp)) { Text("ダウンロード") }
                        }
                    }
                }
            }) { padding ->
                LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    item {
                        Column(Modifier.padding(top=16.dp,bottom=10.dp)) {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                Text(if(BuildConfig.BETA) "Youtube Pandora! Beta" else "Youtube Pandora!",fontSize=12.sp,modifier=Modifier.weight(1f))
                                if(BuildConfig.BETA && p.bubble && android.provider.Settings.canDrawOverlays(context)) TextButton(
                                    onClick={ (context as? android.app.Activity)?.moveTaskToBack(true) }) { Text("バブルへ") }
                                TextButton(onClick={settings=true}) { Text("環境設定") }
                            }
                            Text("ダウンロード",fontSize=32.sp,fontWeight=FontWeight.Bold)
                            Text("お気に入りを、いつでも。",fontSize=13.sp,color=colors.onSurfaceVariant)
                        }
                    }
                    item {
                        Panel(bitmap != null,p.glass) {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Button(onClick={pasteLinks()},enabled=!s.busy,modifier=Modifier.weight(1f)) { Text("リンクを追加") }
                                Button(onClick={text=""},enabled=!s.busy && text.isNotEmpty()) { Text("リンクを削除") }
                            }
                            OutlinedTextField(value=text,onValueChange={text=it},modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=6,
                                placeholder={Text("1行に1つのURL\nYouTube以外の対応サイトも使えます")},enabled=!s.busy,shape=RoundedCornerShape(18.dp))
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick={pasteLinks()},enabled=!s.busy) { Text("貼り付け") }
                                Button(onClick={start("FETCH")},enabled=!s.busy,modifier=Modifier.weight(1f)) { Text("＋ 情報を取得") }
                            }
                            Text("動画＋プレイリストのURL: ${if(p.playlists) "プレイリスト全体" else "動画1件"}",fontSize=11.sp,color=colors.onSurfaceVariant)
                        }
                    }
                    item {
                        Column {
                          Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { TintBadge("保存する動画 (${s.videos.size})",strong=true) }
                            Button(onClick={Store.removeSelected()},enabled=!s.busy && s.videos.any { it.selected }) { Text("動画を削除") }
                          }
                          Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                            TextButton(onClick={Store.state.update { it.copy(videos=it.videos.map { v -> v.copy(selected=true) }) }; Store.save()},enabled=!s.busy) { Text("全選択") }
                            TextButton(onClick={Store.state.update { it.copy(videos=it.videos.map { v -> v.copy(selected=false) }) }; Store.save()},enabled=!s.busy) { Text("解除") }
                          }
                        }
                    }
                    if(s.videos.isEmpty()) item { Panel(bitmap != null,p.glass) { Text("動画を追加すると、サムネイルと時間を表示します。",color=colors.onSurfaceVariant) } }
                    items(s.videos,key={it.key}) { video ->
                        VideoPanel(video,bitmap != null,p.glass,s.busy) { preview=video }
                    }
                    item {
                        Panel(bitmap != null,p.glass) {
                            TintBadge("保存設定",strong=true)
                            Choice("形式",p.format,listOf("MP4","MP3"),!s.busy) { Store.preferences(p.copy(format=it)) }
                            if(p.format == "MP4") {
                                Choice("画質",p.quality,listOf("1080p以下","720p以下","最高"),!s.busy) { Store.preferences(p.copy(quality=it)) }
                                Choice("フレームレート",p.fps,listOf("元のまま","24fps","30fps","60fps",INTERPOLATED_FPS),!s.busy) { Store.preferences(p.copy(fps=it)) }
                            } else Choice("音質",p.audio,listOf("192kbps","320kbps","128kbps"),!s.busy) { Store.preferences(p.copy(audio=it)) }
                            Text("保存先: 内部ストレージ/${saveRelativePath(p.format)}",fontSize=12.sp)
                            Text("互換性のある映像は再圧縮せず保存します。フレーム補間はCPUで中間の動きを生成するため時間がかかります。",fontSize=11.sp,color=colors.onSurfaceVariant)
                        }
                    }
                    item { Text("Android版 ${BuildConfig.VERSION_NAME} · yt-dlp",fontSize=11.sp,color=colors.onSurfaceVariant) }
                }
            }
        }
        if(error.isNotEmpty()) AlertDialog(onDismissRequest={error=""},title={Text("確認")},text={Text(error)},confirmButton={TextButton(onClick={error=""}) { Text("閉じる") }})
        if(logs) LogDialog { logs=false }
        if(!s.busy && !settings && !logs && preview == null && error.isEmpty() && pending == null &&
            (updateState.release != null || updateState.message.isNotEmpty())) UpdateDialog(updateState) { AppUpdates.dismiss() }
        preview?.let { video ->
            var thumbnailTone by remember(video.thumbnail) { mutableStateOf<Color?>(null) }
            AlertDialog(onDismissRequest={preview=null},title={TintBadge(video.title,color=thumbnailTone,maxLines=3)},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(video.thumbnail.isNotBlank()) TintedThumbnail(video.thumbnail,Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(16.dp)),ContentScale.Fit) { thumbnailTone=it }
                Text("${video.site} · ${durationText(video.duration)} · ${video.status}")
                if(video.error.isNotBlank()) {
                    SelectionContainer { Text(video.error,fontSize=12.sp,color=colors.error) }
                    val clipboard = LocalClipboardManager.current
                    TextButton(onClick={clipboard.setText(AnnotatedString("Youtube Pandora! Android ${BuildConfig.VERSION_NAME}\n${video.error}"))}) { Text("エラーをコピー") }
                }
                if(video.savedUri.isNotEmpty()) Button(onClick={runCatching {
                    val uri = Uri.parse(video.savedUri)
                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,context.contentResolver.getType(uri) ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }.onFailure { error="ファイルを開くアプリが見つかりません。ギャラリーまたはファイル管理で保存先を確認してください。新しい保存先はDownload/YoutubePandora!です。" }}) { Text("保存したファイルを開く") }
            }
        },confirmButton={TextButton(onClick={preview=null}) { Text("閉じる") }},dismissButton={TextButton(enabled=!s.busy,onClick={Store.state.update { it.copy(videos=it.videos.filterNot { it.key == video.key }) }; Store.save(); preview=null}) { Text("一覧から削除") }}) }
        if(settings) {
            var imageLoading by remember { mutableStateOf(false) }
            var imageError by remember { mutableStateOf("") }
            val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if(uri != null) scope.launch {
                    imageLoading=true
                    imageError=""
                    runCatching { withContext(Dispatchers.IO) {
                        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,uri)) { decoder, info, _ ->
                            val scale = (max(info.size.width,info.size.height)/1600f).coerceAtLeast(1f)
                            decoder.setTargetSize((info.size.width/scale).toInt(),(info.size.height/scale).toInt())
                            decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                        val target = File(context.filesDir,"wallpaper-${System.currentTimeMillis()}.jpg")
                        target.outputStream().use { decoded.compress(Bitmap.CompressFormat.JPEG,90,it) }; decoded.recycle()
                        val old = Store.state.value.preferences.wallpaper
                        Store.preferences(Store.state.value.preferences.copy(wallpaper=target.absolutePath))
                        if(old.isNotBlank()) File(old).takeIf { it.parentFile == context.filesDir }?.delete()
                    } }.onFailure { imageError="画像を読み込めません: ${it.message}" }
                    imageLoading=false
                }
            }
            if(backgroundSettings) BackgroundPreviewDialog(bitmap,p,imageLoading,imageError,
                onChange={Store.preferences(it)},onPick={pick.launch(arrayOf("image/*"))},
                onDismiss={backgroundSettings=false})
            else AlertDialog(onDismissRequest={settings=false},title={Text("環境設定")},text={
                Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) { Text("ダークモード",Modifier.weight(1f)); Switch(p.dark,{Store.preferences(p.copy(dark=it))}) }
                    Row(verticalAlignment=Alignment.CenterVertically) { Text("プレイリスト全体を追加",Modifier.weight(1f)); Switch(p.playlists,{Store.preferences(p.copy(playlists=it))},enabled=!s.busy) }
                    Text("背景",fontWeight=FontWeight.Bold)
                    Button(onClick={backgroundSettings=true}) { Text("背景をプレビュー・調整") }
                    Text("仕上がりを見ながら、画像・透明度・パネルの透過率・ぼかしを調整できます。",fontSize=12.sp)
                    if(BuildConfig.BETA) BubbleSettings(p)
                    OutlinedButton(onClick={settings=false; start("UPDATE")},enabled=!s.busy) { Text("yt-dlpを更新") }
                    OutlinedButton(onClick={settings=false;logs=true}) { Text("処理ログを見る") }
                    Text("アプリの更新 · 現在 ${BuildConfig.VERSION_NAME}")
                    Text("起動時にGitHubの公開版・試用版を確認し、新しいバージョンがあると通知します。",fontSize=12.sp)
                    OutlinedButton(enabled=!updateState.checking,onClick={settings=false;AppUpdates.check(manual=true)}) {
                        Text(if(updateState.checking) "確認中…" else "新しいバージョンを確認")
                    }
                    OutlinedButton(enabled=!updateState.checking,onClick={settings=false;AppUpdates.check(manual=true,beta=true,offer=true)}) {
                        Text("ベータ版をダウンロード")
                    }
                    OutlinedButton(enabled=!updateState.checking,onClick={settings=false;AppUpdates.check(manual=true,beta=false,offer=true)}) {
                        Text(if(BuildConfig.BETA) "リリース版に戻す" else "リリース版をダウンロード")
                    }
                    Text("配信中ライブ・ログイン必須の動画は対象外です。",fontSize=12.sp)
                }
            },confirmButton={TextButton(onClick={settings=false}) { Text("閉じる") }})
        }
    }
}

@Composable
internal fun Panel(image: Boolean, glass: Float, content: @Composable ColumnScope.()->Unit) {
    Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=if(image) 1-glass else 1f),contentColor=MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}
@Composable
private fun Choice(title: String, value: String, options: List<String>, enabled: Boolean, onSelect: (String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.weight(1f).padding(end=8.dp)) { TintBadge(title) }
        Box {
            OutlinedButton(onClick={expanded=true},enabled=enabled) { Text("$value  ${if(expanded) "▲" else "▼"}") }
            DropdownMenu(expanded,{expanded=false}) { options.forEach { item ->
                DropdownMenuItem(text={Text((if(item == value) "✓  " else "    ")+item)},onClick={onSelect(item);expanded=false})
            } }
        }
    }
}

internal fun accentColor(bitmap: Bitmap, dark: Boolean): Color {
    var best = floatArrayOf(215f,.6f,.7f); var score = -1f
    val hsv = FloatArray(3)
    val buckets = mutableMapOf<Int,Pair<Int,Int>>()
    for(y in 0 until bitmap.height step max(1,bitmap.height/32)) for(x in 0 until bitmap.width step max(1,bitmap.width/32)) {
        val pixel = bitmap.getPixel(x,y); android.graphics.Color.colorToHSV(pixel,hsv)
        val key = (hsv[0]/20).toInt(); val old = buckets[key]; buckets[key] = (old?.first?.plus(1) ?: 1) to pixel
    }
    for((count,pixel) in buckets.values) {
        android.graphics.Color.colorToHSV(pixel,hsv)
        val value = count * (.1f+hsv[1]) * hsv[2]*hsv[2]
        if(value > score) { score=value; best=hsv.clone() }
    }
    best[1]=best[1].coerceIn(.25f,.72f); best[2]=if(dark) .65f else .52f
    return Color(android.graphics.Color.HSVToColor(best))
}


@Composable
internal fun TintBadge(text: String, modifier: Modifier = Modifier, color: Color? = null,
                      strong: Boolean = false, maxLines: Int = 2) {
    val standard = readableBadgeColor(color ?: MaterialTheme.colorScheme.primary)
    Surface(modifier=modifier,shape=RoundedCornerShape(16.dp),
        color=if(strong) strongBadgeColor(standard) else standard,contentColor=Color.White) {
        Text(text,Modifier.padding(horizontal=12.dp,vertical=7.dp),fontSize=13.sp,
            fontWeight=if(strong) FontWeight.Bold else FontWeight.Medium,
            maxLines=maxLines,overflow=TextOverflow.Ellipsis)
    }
}

@Composable
private fun TintedThumbnail(url: String, modifier: Modifier, scale: ContentScale, onTone: (Color?)->Unit) {
    val context = LocalContext.current
    // Reuse Coil's display request/cache; software pixels can safely be sampled.
    val request = remember(context,url) { ImageRequest.Builder(context).data(url).allowHardware(false).build() }
    AsyncImage(request,"サムネイル",modifier,contentScale=scale,
        onSuccess={ state ->
            onTone(runCatching { readableBadgeColor(accentColor(state.result.drawable.toBitmap(48,48),true)) }.getOrNull())
        },onError={onTone(null)})
}


@Composable
private fun VideoPanel(video: Video, image: Boolean, glass: Float, busy: Boolean, onPreview: ()->Unit) {
    var tone by remember(video.thumbnail) { mutableStateOf<Color?>(null) }
    var thumbnailFailed by remember(video.thumbnail) { mutableStateOf(false) }
    val colors=MaterialTheme.colorScheme
    Panel(image,glass) {
        Row {
            Spacer(Modifier.width(48.dp))
            TintBadge(video.title,color=tone,maxLines=2,modifier=Modifier.weight(1f).clickable(onClick=onPreview))
        }
        Row(verticalAlignment=Alignment.CenterVertically) {
            Checkbox(checked=video.selected,onCheckedChange={checked ->
                Store.edit(video.key) { it.copy(selected=checked) }; Store.save()
            },enabled=!busy,modifier=Modifier.width(48.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceVariant).clickable(onClick=onPreview).testTag("thumbnail-${video.key}")) {
                    Text(if(video.thumbnail.isBlank() || thumbnailFailed) "サムネイルを取得できません" else "サムネイルを読み込み中…",Modifier.align(Alignment.Center).padding(12.dp),
                        color=colors.onSurfaceVariant,fontSize=12.sp)
                    if(video.thumbnail.isNotBlank()) TintedThumbnail(video.thumbnail,Modifier.fillMaxSize(),ContentScale.Crop) { tone=it; thumbnailFailed=it == null }
                    Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(8.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        TintBadge(video.site.ifBlank { "サイト不明" },color=tone,maxLines=1,modifier=Modifier.weight(1f))
                        TintBadge(durationText(video.duration),color=tone,maxLines=1)
                    }
                }
                Text(video.status,fontSize=12.sp,color=if(video.status == "失敗") colors.error else colors.onSurfaceVariant)
                if(video.status == "失敗") Text(video.error.ifBlank { "タップして詳細を確認" },
                    Modifier.clickable(onClick=onPreview),maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=11.sp,color=colors.error)
            }
        }
    }
}
