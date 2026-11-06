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
import java.util.concurrent.CancellationException

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var engine: Engine? = null
    private var job: Job? = null
    private var lastUpdate = 0L
    private var conversion = false
    private var wakeLock: PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("downloads","ダウンロード",NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action == "STOP") { engine?.cancel(); if(job?.isActive != true) stopSelf(); return START_NOT_STICKY }
        if(job?.isActive == true) return START_NOT_STICKY
        startForeground(101,notification("準備中",null),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        Store.state.update { it.copy(busy=true,message="準備中…",progress=null,detail="",done=0,total=0) }
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Pandora:Download").apply { acquire(6 * 60 * 60 * 1000L) }
        val operation = intent?.action ?: "FETCH"
        val input = intent?.getStringExtra("urls") ?: ""
        val retry = intent?.getBooleanExtra("retry",false) ?: false
        engine = Engine(this) { phase, value, detail -> report(phase,value,detail) }
        job = scope.launch {
            var message = "完了しました。"
            try {
                engine!!.init()
                when(operation) {
                    "UPDATE" -> { engine!!.update(); message = "yt-dlpを更新しました。" }
                    "FETCH" -> {
                        val urls = validUrls(input); require(urls.isNotEmpty()) { "URLを入力してください。" }
                        var failures = 0
                        for(url in urls) {
                            engine!!.check()
                            try {
                                val found = engine!!.discover(url,Store.state.value.preferences.playlists)
                                Store.state.update { s -> s.copy(videos=(s.videos + found).distinctBy { it.key }) }
                                Store.save()
                            } catch(e: Exception) { engine!!.check(); failures++; message = e.message ?: "情報を取得できません。" }
                        }
                        message = if(failures == 0) "情報を取得しました。保存する動画を選んでください。" else "$failures 件のURLで失敗: $message"
                    }
                    "DOWNLOAD" -> {
                        val s = Store.state.value
                        val queue = s.videos.filter { if(retry) it.status == "失敗" else it.selected && it.status != "完了" }
                        require(queue.isNotEmpty()) { "保存する動画を選択してください。" }
                        Store.state.update { it.copy(total=queue.size) }
                        var failed = 0
                        for((index,video) in queue.withIndex()) {
                            engine!!.check()
                            Store.state.update { it.copy(current=video.key,done=index,progress=null) }
                            Store.edit(video.key) { it.copy(status="ダウンロード中",error="") }; Store.save()
                            try {
                                val uri = engine!!.download(video,s.preferences)
                                Store.edit(video.key) { it.copy(status="完了",savedUri=uri,error="") }
                            } catch(e: Exception) {
                                engine!!.check(); failed++
                                Store.edit(video.key) { it.copy(status="失敗",error=(e.message ?: "保存できませんでした。").takeLast(2000)) }
                            }
                            Store.state.update { it.copy(done=index+1) }; Store.save()
                        }
                        message = "完了 ${queue.size-failed} 件 / 失敗 $failed 件"
                    }
                }
            } catch(e: CancellationException) {
                message = "停止しました。待機中の動画は残っています。"
                val key = Store.state.value.current
                Store.edit(key) { if(it.status != "完了") it.copy(status="停止") else it }
            } catch(e: Exception) { message = (e.message ?: "処理に失敗しました。").takeLast(2000) }
            finally {
                Store.state.update { it.copy(busy=false,message=message,current="",progress=null,detail="") }
                Store.save()
                if(wakeLock?.isHeld == true) wakeLock?.release()
                withContext(Dispatchers.Main) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            }
        }
        return START_NOT_STICKY
    }
    private fun report(phase: String, value: Float?, detail: String) {
        val now = System.currentTimeMillis()
        if(now-lastUpdate < 200 && phase == Store.state.value.message) return
        lastUpdate = now
        Store.state.update { it.copy(message=phase,progress=value,detail=detail) }
        if(Store.state.value.current.isNotEmpty()) Store.edit(Store.state.value.current) { it.copy(status=phase) }
        val notification = notification(phase,value)
        if(Build.VERSION.SDK_INT >= 35 && phase == "変換中" && !conversion) {
            conversion = true
            startForeground(101,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
        } else getSystemService(NotificationManager::class.java).notify(101,notification)
    }
    private fun notification(text: String, value: Float?): Notification {
        val open = PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this,1,Intent(this,DownloadService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this,"downloads").setSmallIcon(R.drawable.ic_pandora).setContentTitle("Youtube Pandora!")
            .setContentText(text).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .setProgress(100,((value ?: 0f)*100).toInt(),value == null).addAction(0,"停止",stop).build()
    }
    override fun onTimeout(startId: Int, fgsType: Int) { engine?.cancel(); stopSelf() }
    override fun onDestroy() {
        engine?.cancel()
        if(wakeLock?.isHeld == true) wakeLock?.release()
        scope.cancel()
        super.onDestroy()
    }
}
