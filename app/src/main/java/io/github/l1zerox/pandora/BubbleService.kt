package io.github.l1zerox.pandora

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlin.math.abs
import kotlin.math.roundToInt

internal object BubbleRuntime {
    val foreground=MutableStateFlow(false)
    val permission=MutableStateFlow(false)
    val error=MutableStateFlow("")
    fun sync(context: Context) {
        permission.value=Settings.canDrawOverlays(context)
        if(BuildConfig.BETA && Store.state.value.preferences.bubble && permission.value) {
            runCatching { ContextCompat.startForegroundService(context,Intent(context,BubbleService::class.java)) }
                .onFailure { error.value="バブルを開始できませんでした。アプリを開いて再度お試しください。"; AppLog.error("バブル開始",it) }
        } else context.stopService(Intent(context,BubbleService::class.java))
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
    private var progress: ProgressBar? = null
    private var pause: Button? = null
    private var start: Button? = null
    private var cancel: Button? = null
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
            combine(Store.state,BubbleRuntime.foreground) { state,_ -> state }.collect { state -> lastState=state; render() }
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
        if(!BuildConfig.BETA || !Store.state.value.preferences.bubble || !Settings.canDrawOverlays(this)) {
            hide(); stopSelf(); return
        }
        if(BubbleRuntime.foreground.value || getSystemService(KeyguardManager::class.java).isKeyguardLocked) { hide(); return }
        if(bubble == null) showBubble()
        bubble?.update(lastState)
        updatePanel()
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
    private fun button(label: String, action: ()->Unit)=Button(this).apply {
        text=label; isAllCaps=false; textSize=13f; setOnClickListener { action() }
    }
    private fun showPanel() {
        val (width,height)=bounds()
        val container=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(12))
            background=GradientDrawable().apply { setColor(Color.rgb(27,30,39)); cornerRadius=dp(22).toFloat() }
        }
        val header=LinearLayout(this)
        header.addView(text(18f).apply { text="DL管理パネル" },LinearLayout.LayoutParams(0,-2,1f))
        header.addView(button("閉じる") { hidePanel() })
        container.addView(header)
        title=text(16f).also { container.addView(it) }
        phase=text().also { container.addView(it) }
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).also { it.max=100; container.addView(it,LinearLayout.LayoutParams(-1,dp(12))) }
        metrics=text().also { container.addView(it) }
        val actions=LinearLayout(this)
        pause=button("一時停止") { command(if(Store.state.value.paused) "RESUME" else "PAUSE") }
        cancel=button("キャンセル") { command("CANCEL") }
        actions.addView(pause,LinearLayout.LayoutParams(0,-2,1f)); actions.addView(cancel,LinearLayout.LayoutParams(0,-2,1f))
        container.addView(actions)
        start=button("待機キューをダウンロード") { command("DOWNLOAD") }.also { container.addView(it) }
        queue=text().also { container.addView(it) }
        container.addView(text(12f).apply { text="YouTubeの「共有」→「Youtube Pandora!」でキューに追加できます。ゲージは現在の処理の進捗です。" })
        container.addView(button("アプリを開く") {
            runCatching { startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }.onFailure(::failed)
        })
        container.addView(button("バブルを終了") { Store.preferences(Store.state.value.preferences.copy(bubble=false)); stopSelf() })
        val view=ScrollView(this).apply { addView(container); isFillViewport=true }
        val p=params(minOf(dp(340),width-dp(16)).coerceAtLeast(1),minOf(dp(500),(height*.7f).roundToInt()).coerceAtLeast(1)).apply {
            x=((bubbleParams?.x ?: 0)-width/2).coerceAtLeast(0); y=(bubbleParams?.y ?: 0)+dp(72); clamp(this)
        }
        runCatching { windows.addView(view,p); panel=view; panelParams=p; updatePanel() }.onFailure(::failed)
    }
    private fun updatePanel() {
        if(panel == null) return
        val s=lastState
        title?.text=s.videos.firstOrNull { it.key == s.current }?.title ?: "待機キュー ${s.videos.count { it.selected && it.status != "完了" }} 件"
        phase?.text=(if(s.paused) "一時停止中" else s.message)+"\n処理済み ${s.done} / ${s.total} 件"
        val fraction=s.progress?.takeIf { it.isFinite() }?.coerceIn(0f,1f)
        progress?.isIndeterminate=s.busy && fraction == null && !s.paused
        progress?.progress=((fraction ?: 0f)*100).roundToInt()
        metrics?.text="進捗 ${fraction?.let { "${(it*100).roundToInt()}%" } ?: "不明"}\n速度 ${s.speed.ifBlank { "—" }} · 残り ${s.eta?.let(::durationText) ?: "不明"}"
        pause?.text=if(s.paused) "再開" else "一時停止"
        pause?.isEnabled=s.busy && s.operation == "DOWNLOAD" && !s.stopping
        cancel?.isEnabled=s.busy && !s.stopping
        start?.isEnabled=!s.busy && s.videos.any { it.selected && it.status != "完了" }
        val pending=s.videos.filter { it.selected && it.status != "完了" }
        queue?.text=pending.take(6).joinToString("\n",prefix="キュー\n") { "${it.status} · ${it.title.take(70)}" }+
            if(pending.size > 6) "\nほか ${pending.size-6} 件" else ""
    }
    private fun command(action: String) {
        runCatching {
            val intent=Intent(this,DownloadService::class.java).setAction(action)
            if(action == "DOWNLOAD") ContextCompat.startForegroundService(this,intent) else startService(intent)
        }.onFailure { AppLog.error("バブル操作",it); Toast.makeText(this,"アプリを開いて再度操作してください。",Toast.LENGTH_LONG).show() }
    }
    private fun hidePanel() {
        panel?.let { runCatching { windows.removeView(it) } }; panel=null; panelParams=null
        title=null; phase=null; progress=null; metrics=null; queue=null; pause=null; cancel=null; start=null
    }
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
    private val ring=RectF()
    private val icon=ContextCompat.getDrawable(context,R.drawable.ic_pandora)
    private var state=ScreenState()
    init { isClickable=true; isFocusable=true; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES }
    fun update(value: ScreenState) {
        state=value; contentDescription="Pandora DL管理パネルを開く。${if(value.paused) "一時停止中" else value.message}"
        invalidate()
    }
    override fun performClick(): Boolean { super.performClick(); open(); return true }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size=minOf(width,height).toFloat(); val center=size/2
        paint.style=Paint.Style.FILL; paint.color=Color.rgb(28,33,46)
        canvas.drawCircle(center,center,center-3,paint)
        paint.style=Paint.Style.STROKE; paint.strokeWidth=size*.065f; paint.strokeCap=Paint.Cap.ROUND
        ring.set(size*.075f,size*.075f,size*.925f,size*.925f)
        paint.color=Color.rgb(67,76,96); canvas.drawOval(ring,paint)
        paint.color=if(state.paused) Color.rgb(255,193,71) else Color.rgb(67,136,255)
        val fraction=state.progress?.takeIf { it.isFinite() }?.coerceIn(0f,1f)
        if(fraction != null) canvas.drawArc(ring,-90f,fraction*360,false,paint)
        else if(state.busy && !state.paused) {
            canvas.drawArc(ring,(android.os.SystemClock.uptimeMillis()%1800)/5f,85f,false,paint); postInvalidateDelayed(80)
        }
        icon?.setBounds((size*.29f).roundToInt(),(size*.22f).roundToInt(),(size*.71f).roundToInt(),(size*.64f).roundToInt())
        icon?.draw(canvas)
        paint.style=Paint.Style.FILL; paint.color=Color.WHITE; paint.textAlign=Paint.Align.CENTER; paint.textSize=size*.17f
        canvas.drawText(if(state.paused) "Ⅱ" else fraction?.let { "${(it*100).roundToInt()}%" } ?: "DL",center,size*.81f,paint)
    }
}
