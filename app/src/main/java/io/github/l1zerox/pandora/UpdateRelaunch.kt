package io.github.l1zerox.pandora

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Kept separately from the APK record, which is cleaned up during Application.onCreate. */
internal class UpdateRelaunchStore(private val data: SharedPreferences) {
    fun read()=parseUpdateRelaunch(data.getString("request",null))
    fun arm(request: UpdateRelaunchRequest) {
        check(data.edit().putString("request",request.json()).commit()) { "更新後の起動設定を保存できませんでした。もう一度お試しください。" }
    }
    fun clear() { check(data.edit().remove("request").commit()) { "更新後の起動設定を解除できませんでした。" } }
    fun consume(version: String, code: Int, now: Long): Boolean {
        val request=read()
        clear() // Persist consumption before starting any UI, including when the record has expired.
        return request?.eligible(version,code,now) == true
    }
}

internal object UpdateRelaunch {
    private const val CHANNEL="app_updates"
    private const val NOTIFICATION=9401
    private fun store(context: Context)=UpdateRelaunchStore(context.getSharedPreferences("update_relaunch",Context.MODE_PRIVATE))

    fun arm(context: Context, version: String) {
        store(context).arm(UpdateRelaunchRequest(version,BuildConfig.VERSION_CODE,System.currentTimeMillis()))
    }
    fun cancel(context: Context) {
        runCatching { store(context).clear() }.onFailure { AppLog.error("更新後の起動",it) }
    }
    fun launchIntent(context: Context)=Intent(context,MainActivity::class.java).setAction(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    fun openApp(context: Context) { context.startActivity(launchIntent(context)) }

    fun onPackageReplaced(context: Context) {
        val requested=runCatching { store(context).consume(BuildConfig.VERSION_NAME,BuildConfig.VERSION_CODE,System.currentTimeMillis()) }
            .onFailure { AppLog.error("更新後の起動",it) }.getOrDefault(false)
        if(!requested) return
        // BAL denial can be silent. Post the fallback first; MainActivity removes it once visible.
        runCatching { notifyCompleted(context) }.onFailure { AppLog.error("更新完了通知",it) }
        runCatching { openApp(context) }
            .onSuccess { AppLog.info("更新後の起動","更新完了後の画面表示を要求しました。端末が制限する場合は通知から開けます。") }
            .onFailure { AppLog.error("更新後の起動",it) }
    }

    fun onAppVisible(context: Context) {
        runCatching {
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
            val store=store(context)
            if(store.read()?.installed(BuildConfig.VERSION_NAME,BuildConfig.VERSION_CODE) == true) store.clear()
        }.onFailure { AppLog.error("更新完了通知の整理",it) }
    }

    private fun notifyCompleted(context: Context) {
        if(Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            AppLog.info("更新完了通知","通知が許可されていません。自動で開かない場合はホーム画面から開いてください。")
            return
        }
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"アプリの更新",NotificationManager.IMPORTANCE_DEFAULT))
        val open=PendingIntent.getActivity(context,NOTIFICATION,launchIntent(context),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(NOTIFICATION,NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_pandora)
            .setContentTitle("更新が完了しました").setContentText("Youtube Pandora! ${BuildConfig.VERSION_NAME} を開く")
            .setContentIntent(open).addAction(0,"アプリを開く",open).setAutoCancel(true).setOnlyAlertOnce(true).build())
    }
}

class UpdateReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if(intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) UpdateRelaunch.onPackageReplaced(context)
    }
}
