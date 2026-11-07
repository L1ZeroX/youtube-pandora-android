package io.github.l1zerox.pandora

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.update

class DownloadService : Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    @Volatile private var engine: Engine? = null
    private var job: Job? = null
    private var batch: DownloadBatch? = null
    private var lastUpdate=0L
    private var conversion=false
    private var destroyed=false
    private var wakeLock: PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("downloads","ダウンロード",NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action=intent?.action ?: return START_NOT_STICKY
        val state=Store.state.value
        when(action) {
            "STOP", "CANCEL" -> {
                if(job != null) {
                    Store.state.update { it.copy(stopping=true,paused=false,message="キャンセルしています…") }
                    engine?.cancel()
                } else if(batch != null) {
                    val pausedBatch=batch
                    Store.state.update { it.copy(stopping=true,paused=false) }
                    job=scope.launch {
                        pausedBatch?.workspace?.deleteRecursively()
                        Store.edit(Store.state.value.current) { if(it.status == "完了") it else it.copy(status="キャンセル") }
                        withContext(NonCancellable+Dispatchers.Main) { finish("キャンセルしました。待機中の動画は残っています。") }
                    }
                } else stopSelf()
            }
            "PAUSE" -> if(state.operation == "DOWNLOAD" && job != null && !state.stopping && !state.paused) {
                Store.state.update { it.copy(stopping=true,message="一時停止しています…") }
                engine?.pause()
            }
            "RESUME" -> if(state.paused && job == null && batch != null) launchOperation("DOWNLOAD",resuming=true)
            "FETCH", "REFRESH", "DOWNLOAD", "UPDATE" -> if(job == null && !state.busy) {
                batch=if(action == "DOWNLOAD") DownloadBatch(state.preferences,state.videos,intent.getBooleanExtra("retry",false)) else null
                launchOperation(action,intent.getStringExtra("urls").orEmpty())
            }
            else -> if(job == null && batch == null) stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun launchOperation(operation: String, input: String = "", resuming: Boolean = false) {
        conversion=false; lastUpdate=0
        try {
            startForeground(101,notification(if(resuming) "再開しています…" else "準備中",null),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch(e: Exception) { AppLog.error("処理開始",e); finish("処理を開始できませんでした。アプリを開いて再度お試しください。"); return }
        Store.state.update { it.copy(busy=true,paused=false,stopping=false,operation=operation,
            message=if(resuming) "再開しています…" else "準備中…",progress=null,detail="",speed="",eta=null,
            done=if(resuming) it.done else 0,total=if(resuming) it.total else 0) }
        wakeLock=(getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Pandora:Download")
            .apply { acquire(6*60*60*1000L) }
        val processor=Engine(this) { phase,value,detail -> report(phase,value,detail) }
        engine=processor
        val activeBatch=batch
        job=scope.launch {
            var message="完了しました。"
            try {
                AppLog.info("処理開始","$operation / 再開=$resuming")
                processor.init(installBundled=operation != "UPDATE")
                when(operation) {
                    "UPDATE" -> { processor.update(); message="yt-dlpを更新しました。" }
                    "FETCH", "REFRESH" -> {
                        val urls=validUrls(input); require(urls.isNotEmpty()) { "URLを入力してください。" }
                        var failures=0
                        for(url in urls) {
                            processor.check()
                            try {
                                val found=processor.discover(url,operation != "REFRESH" && Store.state.value.preferences.playlists)
                                Store.state.update { s -> s.copy(videos=mergeDiscoveredVideos(s.videos,found)) }
                                Store.save()
                            } catch(e: Exception) { processor.check(); AppLog.error("情報取得",e); failures++; message=e.message.orEmpty() }
                        }
                        message=if(failures == 0) "情報を取得しました。保存する動画を選んでください。" else "$failures 件のURLで失敗: $message"
                    }
                    "DOWNLOAD" -> message=downloadBatch(processor,requireNotNull(activeBatch))
                }
            } catch(e: CancellationException) {
                val paused=processor.paused
                message=if(paused) "一時停止中" else "キャンセルしました。待機中の動画は残っています。"
                Store.edit(Store.state.value.current) { if(it.status == "完了") it else it.copy(status=if(paused) "一時停止" else "キャンセル") }
            } catch(e: Exception) {
                AppLog.error("処理エラー",e); message=(e.message ?: "処理に失敗しました。").takeLast(2000)
            } finally {
                releaseWakeLock()
                withContext(NonCancellable+Dispatchers.Main) {
                    job=null
                    if(!destroyed && processor.paused && activeBatch != null && activeBatch.remaining(Store.state.value.videos).isNotEmpty()) {
                        Store.state.update { it.copy(paused=true,stopping=false,message="一時停止中",speed="",eta=null,
                            detail="取得途中のデータを保持しています。再開すると続きから取得します。変換は最初から再実行します。") }
                        Store.save()
                        getSystemService(NotificationManager::class.java).notify(101,notification("一時停止中",Store.state.value.progress))
                        AppLog.info("一時停止","部分ファイルを保持しました。")
                    } else finish(message)
                }
            }
        }
    }
    private fun downloadBatch(processor: Engine, active: DownloadBatch): String {
        require(active.remaining(Store.state.value.videos).isNotEmpty()) { "保存する動画を選択してください。" }
        while(true) {
            processor.check()
            val remaining=active.remaining(Store.state.value.videos)
            var video=remaining.firstOrNull() ?: break
            Store.state.update { it.copy(current=video.key,done=active.attempted.size,total=active.attempted.size+remaining.size,progress=null,speed="",eta=null) }
            Store.edit(video.key) { it.copy(status="ダウンロード中",error="") }; Store.save()
            try {
                if(!video.resolved) {
                    val resolved=processor.discover(video.url,false).firstOrNull() ?: error("動画情報を取得できませんでした。")
                    video=video.withMetadata(resolved)
                    val updated=video
                    Store.edit(video.key) { updated.copy(status="ダウンロード中") }; Store.save()
                }
                active.workspace=transferWorkspace(cacheDir,video,active.preferences)
                val uri=processor.download(video,active.preferences,active.workspace)
                Store.edit(video.key) { it.copy(status="完了",savedUri=uri,error="") }
                active.workspace?.deleteRecursively(); active.workspace=null
                AppLog.info("動画完了","${video.key} / $uri")
            } catch(e: Exception) {
                processor.check()
                active.failed++; AppLog.error("動画保存",e)
                Store.edit(video.key) { it.copy(status="失敗",error=downloadFailureMessage(video.url,e)) }
            }
            active.attempted.add(video.key)
            Store.state.update { it.copy(done=active.attempted.size) }; Store.save()
        }
        return "完了 ${active.attempted.size-active.failed} 件 / 失敗 ${active.failed} 件"
    }
    private fun report(phase: String, value: Float?, detail: String) {
        if(Store.state.value.stopping || Store.state.value.paused) return
        val now=System.currentTimeMillis()
        if(now-lastUpdate < 200 && phase == Store.state.value.message) return
        lastUpdate=now
        val metrics=transferProgress(detail)
        val visibleDetail=metrics?.detail ?: detail
        AppLog.progress(phase,visibleDetail)
        Store.state.update { it.copy(message=phase,progress=metrics?.fraction ?: value,detail=visibleDetail,speed=metrics?.speed.orEmpty(),eta=metrics?.eta) }
        if(Store.state.value.current.isNotEmpty()) Store.edit(Store.state.value.current) { it.copy(status=phase) }
        val notice=notification(phase,Store.state.value.progress)
        if(Build.VERSION.SDK_INT >= 35 && phase == "変換中" && !conversion) {
            conversion=true
            startForeground(101,notice,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
        } else getSystemService(NotificationManager::class.java).notify(101,notice)
    }
    private fun notification(text: String, value: Float?): Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun action(name: String, code: Int)=PendingIntent.getService(this,code,Intent(this,DownloadService::class.java).setAction(name),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder=NotificationCompat.Builder(this,"downloads").setSmallIcon(R.drawable.ic_pandora).setContentTitle("Youtube Pandora!")
            .setContentText(text).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .setProgress(100,((value ?: 0f)*100).toInt(),value == null)
        if(Store.state.value.operation == "DOWNLOAD") {
            val paused=Store.state.value.paused
            builder.addAction(0,if(paused) "再開" else "一時停止",action(if(paused) "RESUME" else "PAUSE",2))
        }
        return builder.addAction(0,"キャンセル",action("CANCEL",1)).build()
    }
    private fun releaseWakeLock() { if(wakeLock?.isHeld == true) wakeLock?.release(); wakeLock=null }
    private fun finish(message: String) {
        batch=null; job=null
        AppLog.info("処理終了",message)
        Store.state.update { it.copy(busy=false,paused=false,stopping=false,operation="",message=message,current="",progress=null,detail="",speed="",eta=null) }
        Store.save(); releaseWakeLock()
        if(!destroyed) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        AppLog.errorMessage("OSの実行時間制限","Androidのバックグラウンド処理時間上限に達しました。アプリから再開してください。")
        engine?.cancel(); stopSelf()
    }
    override fun onDestroy() {
        destroyed=true
        if(job != null) engine?.cancel()
        releaseWakeLock(); scope.cancel()
        if(job == null) { Store.state.update { it.copy(busy=false,paused=false,stopping=false,operation="") }; Store.save() }
        super.onDestroy()
    }
}
