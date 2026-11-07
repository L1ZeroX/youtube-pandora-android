package io.github.l1zerox.pandora

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.toArgb
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlin.math.abs
import kotlin.math.roundToInt

internal object BubbleRuntime {
    val foreground=MutableStateFlow(false)
    private val visible=java.util.Collections.newSetFromMap(java.util.WeakHashMap<Activity,Boolean>())
    fun enter(activity: Activity) { visible.add(activity); foreground.value=true }
    fun leave(activity: Activity) { visible.remove(activity); foreground.value=visible.isNotEmpty() }
    val permission=MutableStateFlow(false)
    val error=MutableStateFlow("")
    fun sync(context: Context): Boolean {
        permission.value=Settings.canDrawOverlays(context)
        if(Store.state.value.preferences.bubble && permission.value) {
            return runCatching { ContextCompat.startForegroundService(context,Intent(context,BubbleService::class.java)) }
                .onFailure { error.value="バブルを開始できませんでした。アプリを開いて再度お試しください。"; AppLog.error("バブル開始",it) }
                .isSuccess
        } else context.stopService(Intent(context,BubbleService::class.java))
        return false
    }
}

/** A separate foreground service owns only the overlay; dismissing it never stops downloads. */
class BubbleService : Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private lateinit var windows: WindowManager
    private var bubble: ProgressBubble? = null
    private var panel: ScrollView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var title: TextView? = null
    private var phase: TextView? = null
    private var metrics: TextView? = null
    private var queue: TextView? = null
    private var queueRows: LinearLayout? = null
    private var queueSnapshot: List<Video>? = null
    private var queuePage=0
    private var renderedPage=-1
    private var previousPage: Button? = null
    private var nextPage: Button? = null
    private val queueDeletes=mutableListOf<Button>()
    private var mp4: Button? = null
    private var mp3: Button? = null
    private var formatHint: TextView? = null
    private var artworkSettings: Button? = null
    private var progress: ProgressBar? = null
    private var pause: Button? = null
    private var start: Button? = null
    private var cancel: Button? = null
    private var clipboardStatus: TextView? = null
    private var panelArtwork: Bitmap? = null
    private var panelColor: androidx.compose.ui.graphics.Color? = null
    private val panelButtons=mutableListOf<Button>()
    private var palette=bubblePalette()
    private var artwork: Bitmap? = null
    private var artworkUrl=""
    private var artworkJob: Job? = null
    private val clipboard by lazy {
        BubbleClipboard(this,{
            Store.state.value.preferences.let { it.bubble && it.bubbleClipboard } &&
                panel?.hasWindowFocus() == true && !getSystemService(KeyguardManager::class.java).isKeyguardLocked
        },{ clipboardStatus?.text=it })
    }
    private var lastState=ScreenState()
    private val positions by lazy { getSharedPreferences("bubble-window",MODE_PRIVATE) }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun dp(value: Int)=(value*resources.displayMetrics.density).roundToInt()
    override fun onCreate() {
        super.onCreate()
        windows=getSystemService(WindowManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("bubble","バブル管理パネル",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,20,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val close=PendingIntent.getService(this,21,Intent(this,BubbleService::class.java).setAction("CLOSE"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notice=NotificationCompat.Builder(this,"bubble").setSmallIcon(R.drawable.ic_pandora).setContentTitle("Pandora バブル")
            .setContentText("他のアプリ上でダウンロードを管理できます").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0,"バブルを終了",close).build()
        try {
            if(Build.VERSION.SDK_INT >= 34) startForeground(202,notice,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(202,notice)
        } catch(e: Exception) { failed(e); return }
        scope.launch {
            combine(Store.state,BubbleRuntime.foreground) { state,_ -> state }.collect { state ->
                lastState=state
                render()
            }
        }
        scope.launch { while(isActive) { delay(1000); render() } }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action == "CLOSE") {
            Store.preferences(Store.state.value.preferences.copy(bubble=false)); stopSelf()
        } else render()
        return START_NOT_STICKY
    }
    private fun render() {
        if(!Store.state.value.preferences.bubble || !Settings.canDrawOverlays(this)) {
            hide(); stopSelf(); return
        }
        updateArtwork()
        if(BubbleRuntime.foreground.value || getSystemService(KeyguardManager::class.java).isKeyguardLocked) { hide(); return }
        if(bubble == null) showBubble()
        bubble?.update(lastState,palette,artwork)
        updatePanel()
    }
    private fun updateArtwork() {
        val video=bubbleVideo(lastState)
        val url=video?.let { it.thumbnail.ifBlank { youtubeThumbnail(it.url) } }.orEmpty()
        if(url == artworkUrl) return
        artworkUrl=url; artworkJob?.cancel(); palette=bubblePalette(); artwork=null
        panelArtwork=null; panelColor=null; refreshPanelBackground()
        if(url.isBlank()) return
        artworkJob=scope.launch {
            var bitmap: Bitmap? = null
            for(attempt in 0..2) {
                bitmap=try {
                    val result=imageLoader.execute(ImageRequest.Builder(this@BubbleService).data(url)
                        .size(dp(96),dp(96)).scale(Scale.FILL).allowHardware(false).build())
                    (result as? SuccessResult)?.drawable?.toBitmap()
                } catch(e: CancellationException) { throw e } catch(_: Exception) { null }
                if(bitmap != null) break
                if(attempt < 2) delay(1500L*(attempt+1))
            }
            val glass=withContext(Dispatchers.Default) {
                bitmap?.let { blurThumbnail(it,maxEdge=96,passes=6) to libraryThumbnailColor(it) }
            }
            // A slow response from a previous queue item must not recolor the current one.
            if(artworkUrl == url) {
                artwork=bitmap
                panelArtwork=glass?.first; panelColor=glass?.second; refreshPanelBackground()
                palette=bitmap?.let { bubblePalette(accentColor(it,true)) } ?: bubblePalette()
                bubble?.update(lastState,palette,artwork); updatePanel()
            }
        }
    }
    private fun bounds(): Pair<Int,Int> {
        if(Build.VERSION.SDK_INT >= 30) return windows.currentWindowMetrics.bounds.let { it.width() to it.height() }
        return resources.displayMetrics.let { it.widthPixels to it.heightPixels }
    }
    private fun params(width: Int,height: Int)=WindowManager.LayoutParams(width,height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.START }
    private fun clamp(p: WindowManager.LayoutParams) {
        val (width,height)=bounds()
        p.x=p.x.coerceIn(0,(width-p.width).coerceAtLeast(0))
        p.y=p.y.coerceIn(0,(height-p.height-dp(48)).coerceAtLeast(0))
    }
    private fun showBubble() {
        val view=ProgressBubble(this) { if(panel == null) showPanel() else hidePanel() }
        val p=params(dp(68),dp(68)).apply { x=positions.getInt("x",bounds().first-dp(76)); y=positions.getInt("y",dp(180)); clamp(this) }
        var downX=0f; var downY=0f; var oldX=0; var oldY=0; var moved=false
        view.setOnTouchListener { v,event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX=event.rawX; downY=event.rawY; oldX=p.x; oldY=p.y; moved=false; true }
                MotionEvent.ACTION_MOVE -> {
                    if(abs(event.rawX-downX)+abs(event.rawY-downY) > ViewConfiguration.get(this).scaledTouchSlop) moved=true
                    if(moved) {
                        hidePanel(); p.x=oldX+(event.rawX-downX).roundToInt(); p.y=oldY+(event.rawY-downY).roundToInt(); clamp(p)
                        runCatching { windows.updateViewLayout(v,p) }.onFailure(::failed)
                    }; true
                }
                MotionEvent.ACTION_UP -> {
                    if(!moved) v.performClick()
                    positions.edit().putInt("x",p.x).putInt("y",p.y).apply(); true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        runCatching { windows.addView(view,p); bubble=view; bubbleParams=p }.onFailure(::failed)
    }
    private fun text(size: Float=14f)=TextView(this).apply { setTextColor(Color.WHITE); textSize=size; setPadding(0,dp(4),0,dp(4)) }
    private fun button(label: String, register: Boolean = true, action: ()->Unit)=Button(this).apply {
        text=label; isAllCaps=false; textSize=13f; setOnClickListener { action() }
        if(register) panelButtons.add(this)
    }
    private fun showPanel() {
        val (width,height)=bounds()
        val container=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(12))
        }
        val header=LinearLayout(this)
        header.addView(text(18f).apply { text="DL管理パネル" },LinearLayout.LayoutParams(0,-2,1f))
        header.addView(button("閉じる") { hidePanel() })
        container.addView(header)
        title=text(16f).also { container.addView(it) }
        phase=text().also { container.addView(it) }
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).also { it.max=100; container.addView(it,LinearLayout.LayoutParams(-1,dp(12))) }
        metrics=text().also { container.addView(it) }
        container.addView(text(14f).apply { text="保存形式" })
        val formats=LinearLayout(this)
        mp4=button("MP4 動画") { Store.downloadFormat("MP4") }
        mp3=button("MP3 音声") { Store.downloadFormat("MP3") }
        formats.addView(mp4,LinearLayout.LayoutParams(0,-2,1f)); formats.addView(mp3,LinearLayout.LayoutParams(0,-2,1f))
        container.addView(formats)
        formatHint=text(12f).also { container.addView(it) }
        artworkSettings=button("アルバムアート設定") {
            runCatching { startActivity(Intent(this,MainActivity::class.java).setAction(OPEN_ARTWORK)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }.onFailure(::failed)
        }.also { container.addView(it) }
        val actions=LinearLayout(this)
        pause=button("一時停止") { command(if(Store.state.value.paused) "RESUME" else "PAUSE") }
        cancel=button("キャンセル") { command("CANCEL") }
        actions.addView(pause,LinearLayout.LayoutParams(0,-2,1f)); actions.addView(cancel,LinearLayout.LayoutParams(0,-2,1f))
        container.addView(actions)
        start=button("待機キューをダウンロード") { command("DOWNLOAD") }.also { container.addView(it) }
        queue=text().also { container.addView(it) }
        queueRows=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }.also { container.addView(it) }
        val pages=LinearLayout(this)
        previousPage=button("前の8件") { queuePage--; updatePanel() }
        nextPage=button("次の8件") { queuePage++; updatePanel() }
        pages.addView(previousPage,LinearLayout.LayoutParams(0,-2,1f)); pages.addView(nextPage,LinearLayout.LayoutParams(0,-2,1f))
        container.addView(pages)
        clipboardStatus=text(12f).apply {
            text=if(lastState.preferences.bubbleClipboard) "コピーしたYouTube URLを確認します…" else "クリップボードの自動追加はオフです。"
        }.also { container.addView(it) }
        container.addView(text(12f).apply { text="YouTubeの「共有」→「Youtube Pandora!」でキューに追加できます。ゲージは現在の処理の進捗です。" })
        container.addView(button("アプリを開く") {
            runCatching { startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }.onFailure(::failed)
        })
        container.addView(button("バブルを終了") { Store.preferences(Store.state.value.preferences.copy(bubble=false)); stopSelf() })
        val view=object: ScrollView(this) {
            private var backDispatcher: android.window.OnBackInvokedDispatcher? = null
            private var backCallback: android.window.OnBackInvokedCallback? = null
            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                if(Build.VERSION.SDK_INT >= 33) {
                    backDispatcher=findOnBackInvokedDispatcher()
                    backCallback=android.window.OnBackInvokedCallback { hidePanel() }.also {
                        backDispatcher?.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY,it)
                    }
                }
            }
            override fun onDetachedFromWindow() {
                if(Build.VERSION.SDK_INT >= 33) backCallback?.let { backDispatcher?.unregisterOnBackInvokedCallback(it) }
                backCallback=null; backDispatcher=null
                super.onDetachedFromWindow()
            }
            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus); clipboard.focusChanged(hasWindowFocus)
            }
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if(event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if(event.action == KeyEvent.ACTION_UP) hidePanel()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            addView(container); isFillViewport=true; isFocusableInTouchMode=true
            background=panelGlassBackground(); clipToOutline=true
            setOnTouchListener { _,event ->
                if(event.actionMasked == MotionEvent.ACTION_OUTSIDE) { hidePanel(); true } else false
            }
        }
        val p=params(minOf(dp(340),width-dp(16)).coerceAtLeast(1),minOf(dp(500),(height*.7f).roundToInt()).coerceAtLeast(1)).apply {
            // Only the explicitly opened panel takes focus. The small bubble never does.
            flags=WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            softInputMode=WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            x=((bubbleParams?.x ?: 0)-width/2).coerceAtLeast(0); y=(bubbleParams?.y ?: 0)+dp(72); clamp(this)
        }
        runCatching { windows.addView(view,p); panel=view; panelParams=p; view.requestFocus(); updatePanel() }.onFailure(::failed)
    }
    private fun updatePanel() {
        if(panel == null) return
        val s=lastState
        updateQueue(s)
        title?.text=bubbleVideo(s)?.title ?: "待機キュー ${s.videos.count { it.selected && it.status != "完了" }} 件"
        val accent=palette.accent.toArgb()
        val buttonTints=ColorStateList(arrayOf(intArrayOf(android.R.attr.state_enabled),intArrayOf()),intArrayOf(accent,palette.track.toArgb()))
        (panelButtons+queueDeletes).forEach { it.backgroundTintList=buttonTints; it.setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_enabled),intArrayOf()),intArrayOf(Color.WHITE,0xFFBBBBBB.toInt()))) }
        mp4?.text=if(s.preferences.format == "MP4") "✓ MP4 動画" else "MP4 動画"
        mp3?.text=if(s.preferences.format == "MP3") "✓ MP3 音声" else "MP3 音声"
        mp4?.isEnabled=!s.busy; mp3?.isEnabled=!s.busy
        mp4?.isSelected=s.preferences.format == "MP4"; mp3?.isSelected=s.preferences.format == "MP3"
        artworkSettings?.visibility=if(s.preferences.format == "MP3") View.VISIBLE else View.GONE
        artworkSettings?.text="アルバムアート: ${s.preferences.artworkMode} · 変更"
        artworkSettings?.isEnabled=!s.busy
        formatHint?.text=if(s.busy) "DL中・一時停止中は形式変更とキュー削除をロックします。変更する場合はキャンセルしてください。"
            else "選んだ形式で待機キューを保存します。削除は一覧のみで、保存済みファイルは残ります。"
        val progressColor=artworkGlassAccent(panelColor).toArgb()
        progress?.progressTintList=ColorStateList.valueOf(progressColor)
        progress?.indeterminateTintList=ColorStateList.valueOf(progressColor)
        progress?.progressBackgroundTintList=ColorStateList.valueOf(palette.track.toArgb())
        phase?.text=(if(s.paused) "一時停止中" else s.message)+"\n処理済み ${s.done} / ${s.total} 件"
        val fraction=s.progress?.takeIf { it.isFinite() }?.coerceIn(0f,1f)
        progress?.isIndeterminate=s.busy && fraction == null && !s.paused
        progress?.progress=((fraction ?: 0f)*100).roundToInt()
        metrics?.text="進捗 ${fraction?.let { "${(it*100).roundToInt()}%" } ?: "不明"}\n速度 ${s.speed.ifBlank { "—" }} · 残り ${s.eta?.let(::durationText) ?: "不明"}"
        pause?.text=if(s.paused) "再開" else "一時停止"
        pause?.isEnabled=s.busy && s.operation == "DOWNLOAD" && !s.stopping
        cancel?.isEnabled=s.busy && !s.stopping
        start?.text="待機キューを${s.preferences.format}でダウンロード"
        start?.isEnabled=!s.busy && s.videos.any { it.selected && it.status != "完了" }
    }
    private fun updateQueue(s: ScreenState) {
        val rows=queueRows ?: return
        val pageCount=((s.videos.size+7)/8).coerceAtLeast(1)
        queuePage=queuePage.coerceIn(0,pageCount-1)
        queue?.text=if(s.videos.isEmpty()) "キューは空です。" else "キュー ${s.videos.size} 件 · ${queuePage+1} / $pageCount ページ"
        if(queueSnapshot != s.videos || renderedPage != queuePage) {
            queueSnapshot=s.videos; renderedPage=queuePage
            rows.removeAllViews(); queueDeletes.clear()
            for(video in s.videos.drop(queuePage*8).take(8)) {
                val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
                row.addView(text(13f).apply {
                    text="${video.status}${if(video.selected) "" else "（対象外）"}\n${video.title}"
                    maxLines=3; ellipsize=android.text.TextUtils.TruncateAt.END
                },LinearLayout.LayoutParams(0,-2,1f))
                val remove=button("削除",register=false) { Store.removeQueueVideo(video.key) }.apply {
                    contentDescription="キューから削除: ${video.title}"
                }
                queueDeletes.add(remove); row.addView(remove); rows.addView(row)
            }
        }
        queueDeletes.forEach { it.isEnabled=!s.busy }
        previousPage?.visibility=if(pageCount > 1) View.VISIBLE else View.GONE
        nextPage?.visibility=if(pageCount > 1) View.VISIBLE else View.GONE
        previousPage?.isEnabled=queuePage > 0; nextPage?.isEnabled=queuePage < pageCount-1
    }
    private fun command(action: String) {
        runCatching {
            val intent=Intent(this,DownloadService::class.java).setAction(action)
            if(action == "DOWNLOAD") ContextCompat.startForegroundService(this,intent) else startService(intent)
        }.onFailure { AppLog.error("バブル操作",it); Toast.makeText(this,"アプリを開いて再度操作してください。",Toast.LENGTH_LONG).show() }
    }
    private fun hidePanel() {
        clipboard.close()
        panel?.let { runCatching { windows.removeView(it) } }; panel=null; panelParams=null
        title=null; phase=null; progress=null; metrics=null; queue=null; pause=null; cancel=null; start=null
        clipboardStatus=null; panelButtons.clear()
        queueRows=null; queueSnapshot=null; renderedPage=-1; queuePage=0; queueDeletes.clear()
        previousPage=null; nextPage=null; mp4=null; mp3=null; formatHint=null; artworkSettings=null
    }
    private fun panelGlassBackground()=ArtworkGlassDrawable(panelArtwork,artworkGlassTint(panelColor),
        resources.displayMetrics.density,strongFrost=true)
    private fun refreshPanelBackground() { panel?.background=panelGlassBackground() }
    private fun hide() { hidePanel(); bubble?.let { runCatching { windows.removeView(it) } }; bubble=null; bubbleParams=null }
    private fun failed(error: Throwable) {
        BubbleRuntime.error.value="バブルを表示できませんでした。「他のアプリの上に表示」の許可を確認してください。"
        AppLog.error("バブル表示",error); hide(); stopSelf()
    }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); hide(); render() }
    override fun onDestroy() { scope.cancel(); hide(); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy() }
}

private class ProgressBubble @JvmOverloads constructor(context: Context, private val open: ()->Unit = {}): View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val imagePaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val imageMatrix=Matrix()
    private val ring=RectF()
    private val badge=RectF()
    private val icon=ContextCompat.getDrawable(context,R.drawable.ic_pandora)
    private var state=ScreenState()
    private var palette=bubblePalette()
    private var thumbnail: Bitmap? = null
    init { isClickable=true; isFocusable=true; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES }
    fun update(value: ScreenState, colors: BubblePalette, bitmap: Bitmap?) {
        if(thumbnail !== bitmap) {
            thumbnail=bitmap
            imagePaint.shader=bitmap?.let { BitmapShader(it,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP) }
            positionThumbnail()
        }
        state=value; palette=colors; contentDescription="Pandora DL管理パネルを開く。${bubbleVideo(value)?.title.orEmpty()}。${if(value.paused) "一時停止中" else value.message}"
        invalidate()
    }
    private fun positionThumbnail() {
        val bitmap=thumbnail ?: return
        val size=minOf(width,height).toFloat()
        if(size <= 0) return
        val scale=size*.72f/minOf(bitmap.width,bitmap.height)
        imageMatrix.setScale(scale,scale)
        imageMatrix.postTranslate((size-bitmap.width*scale)/2,(size-bitmap.height*scale)/2)
        imagePaint.shader?.setLocalMatrix(imageMatrix)
    }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) { super.onSizeChanged(w,h,oldw,oldh); positionThumbnail() }
    override fun performClick(): Boolean { super.performClick(); open(); return true }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size=minOf(width,height).toFloat(); val center=size/2
        paint.style=Paint.Style.FILL; paint.color=palette.surface.toArgb()
        canvas.drawCircle(center,center,center-3,paint)
        paint.style=Paint.Style.STROKE; paint.strokeWidth=size*.065f; paint.strokeCap=Paint.Cap.ROUND
        ring.set(size*.075f,size*.075f,size*.925f,size*.925f)
        paint.color=palette.track.toArgb(); canvas.drawOval(ring,paint)
        paint.color=palette.ring.toArgb()
        val fraction=state.progress?.takeIf { it.isFinite() }?.coerceIn(0f,1f)
        if(fraction != null) canvas.drawArc(ring,-90f,fraction*360,false,paint)
        else if(state.busy && !state.paused) {
            canvas.drawArc(ring,(android.os.SystemClock.uptimeMillis()%1800)/5f,85f,false,paint); postInvalidateDelayed(80)
        }
        if(thumbnail != null) canvas.drawCircle(center,center,size*.36f,imagePaint)
        else {
            icon?.setBounds((size*.29f).roundToInt(),(size*.22f).roundToInt(),(size*.71f).roundToInt(),(size*.64f).roundToInt())
            icon?.draw(canvas)
        }
        if(state.busy || thumbnail == null) {
            val label=if(state.paused) "Ⅱ" else fraction?.let { "${(it*100).roundToInt()}%" } ?: "DL"
            paint.style=Paint.Style.FILL; paint.textAlign=Paint.Align.CENTER; paint.textSize=size*.16f
            val halfWidth=paint.measureText(label)/2+size*.055f
            badge.set(center-halfWidth,size*.64f,center+halfWidth,size*.85f)
            paint.color=0xDD000000.toInt(); canvas.drawRoundRect(badge,size*.08f,size*.08f,paint)
            paint.color=Color.WHITE; canvas.drawText(label,center,size*.805f,paint)
        }
    }
}
