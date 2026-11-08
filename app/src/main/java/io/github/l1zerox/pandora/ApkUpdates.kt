package io.github.l1zerox.pandora

import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class UpdateFileProvider : FileProvider()
internal enum class ApkStage { IDLE, DOWNLOADING, PATCHING, VERIFYING, READY, ERROR }
internal data class ApkUpdateState(val release: AppRelease? = null, val stage: ApkStage = ApkStage.IDLE,
    val bytes: Long = 0, val total: Long = 0, val message: String = "") {
    val working get() = stage in listOf(ApkStage.DOWNLOADING,ApkStage.PATCHING,ApkStage.VERIFYING)
}

/** DownloadManager keeps the transfer alive; only this app's single update file is managed here. */
internal object ApkUpdates {
    val state=MutableStateFlow(ApkUpdateState())
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val lock=Mutex()
    private lateinit var context: Context
    private var id=0L
    private var usingDelta=false
    private var initialization: Job? = null
    private val prefs get()=context.getSharedPreferences("apk_update",Context.MODE_PRIVATE)
    private val manager get()=context.getSystemService(DownloadManager::class.java)
    private fun file(): File = File(requireNotNull(context.getExternalFilesDir(null)) { "更新用の保存領域を利用できません。" },"updates/update.apk")
    private fun patchFile()=File(file().parentFile,"update.pdelta.gz")

    fun initialize(app: Context) {
        if(::context.isInitialized) return
        context=app.applicationContext
        id=prefs.getLong("id",0)
        usingDelta=prefs.getBoolean("delta",false)
        val release=parseApkRelease(prefs.getString("release",null))
        val installed=release != null && installedApkUpdate(release.version,prefs.getInt("fromCode",0),BuildConfig.VERSION_NAME,BuildConfig.VERSION_CODE)
        if(release != null && id > 0 && !installed) state.value=ApkUpdateState(release,ApkStage.DOWNLOADING)
        else initialization=scope.launch { lock.withLock { runCatching { clearLocked() }.onFailure { AppLog.error("APK整理",it) } } }
    }

    fun start(release: AppRelease) = scope.launch {
        initialization?.join()
        lock.withLock {
            try {
                require(validApkRelease(release)) { "更新先の情報が不正です。更新を確認し直してください。" }
                require(apkVersionCanUpdate(release.version,BuildConfig.VERSION_NAME)) { "現在より古い版へは上書きできません。新しいリリースをお待ちください。" }
                if(state.value.release?.downloadUrl == release.downloadUrl && id > 0 && state.value.stage != ApkStage.ERROR &&
                    !(usingDelta && release.delta == null)) {
                    updateStep { refreshLocked() }; return@withLock
                }
                clearLocked()
                updateStep {
                    enqueue(release,release.delta?.baseVersion == BuildConfig.VERSION_NAME && context.applicationInfo.splitSourceDirs.isNullOrEmpty())
                    refreshLocked()
                }
            } catch(e: Exception) { failure(e,release) }
        }
    }

    private fun enqueue(release: AppRelease, delta: Boolean) {
        usingDelta=delta
        state.value=ApkUpdateState(release,ApkStage.DOWNLOADING,message=if(delta) "変更部分をダウンロードしています…" else "APKをダウンロードしています…")
        require(file().parentFile!!.mkdirs() || file().parentFile!!.isDirectory)
        val request=DownloadManager.Request(Uri.parse(if(delta) requireNotNull(release.delta).url else release.downloadUrl))
            .setTitle("Youtube Pandora! ${release.version}").setDescription(if(delta) "変更部分をダウンロード中" else "アプリの更新をダウンロード中")
            .setMimeType(if(delta) "application/gzip" else "application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context,null,if(delta) "updates/update.pdelta.gz" else "updates/update.apk")
        id=manager.enqueue(request)
        require(prefs.edit().putLong("id",id).putString("release",release.updateJson()).putBoolean("delta",delta)
            .putInt("fromCode",BuildConfig.VERSION_CODE).commit()) { "更新情報を保存できませんでした。" }
    }

    private fun fallbackToFull(release: AppRelease, error: Exception) {
        if(error is CancellationException) throw error
        AppLog.info("アプリ更新","差分を利用できないためAPK全体へ切り替えます: ${error.message}")
        clearLocked()
        // Persist full mode in the request as well, so retries cannot re-download a failed patch.
        enqueue(release.copy(delta=null),false)
        state.value=state.value.copy(message="差分を利用できないため、APK全体をダウンロードしています…")
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        initialization?.join()
        lock.withLock { updateStep { refreshLocked() } }
    }

    private fun updateStep(operation: ()->Unit) = runApkUpdateStep(operation,
        canFallback={usingDelta && state.value.release != null},
        fallback={fallbackToFull(requireNotNull(state.value.release),it)},failed={failure(it)})

    private fun refreshLocked() {
        if(!state.value.working || id <= 0) return
        val release=state.value.release ?: return
        manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            require(cursor.moveToFirst()) { "ダウンロードが見つかりません。再試行してください。" }
            fun number(name: String)=cursor.getLong(cursor.getColumnIndexOrThrow(name))
            val bytes=number(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR).coerceAtLeast(0)
            val total=number(DownloadManager.COLUMN_TOTAL_SIZE_BYTES).coerceAtLeast(0)
            when(number(DownloadManager.COLUMN_STATUS).toInt()) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    if(usingDelta) {
                        val delta=requireNotNull(release.delta)
                        require(delta.baseVersion == BuildConfig.VERSION_NAME)
                        verifyApkBytes(patchFile(),release.copy(size=delta.size,digest=delta.digest,delta=null))
                        state.value=ApkUpdateState(release,ApkStage.PATCHING,0,release.size,"差分を適用しています…")
                        applyApkDelta(File(context.applicationInfo.sourceDir),patchFile(),file(),release.size,release.digest) {
                            state.value=state.value.copy(bytes=it)
                        }
                        verify(file(),release)
                        state.value=ApkUpdateState(release,ApkStage.READY,bytes,total)
                        return
                    }
                    state.value=ApkUpdateState(release,ApkStage.VERIFYING,bytes,total,"APKを確認しています…")
                    verify(file(),release)
                    state.value=ApkUpdateState(release,ApkStage.READY,bytes,total)
                }
                DownloadManager.STATUS_FAILED -> {
                    error("APKのダウンロードに失敗しました（${number(DownloadManager.COLUMN_REASON)}）。通信状態と空き容量を確認して再試行してください。")
                }
                else -> state.value=ApkUpdateState(release,ApkStage.DOWNLOADING,bytes,total,
                    if(number(DownloadManager.COLUMN_STATUS).toInt() == DownloadManager.STATUS_PAUSED) "通信を待っています…"
                    else if(usingDelta) "変更部分をダウンロード中…" else "APKをダウンロード中…")
            }
        } ?: error("ダウンロードの状態を取得できません。")
    }

    private fun verify(file: File, release: AppRelease) {
        verifyApkBytes(file,release)
        val pm=context.packageManager
        val archive=requireNotNull(pm.getPackageArchiveInfo(file.absolutePath,PackageManager.GET_SIGNING_CERTIFICATES)) { "APKを読み取れません。再ダウンロードしてください。" }
        val installed=pm.getPackageInfo(context.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
        val signers=archive.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        val current=installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        require(compatibleUpdate(archive.packageName,context.packageName,archive.versionName,release.version,
            archive.longVersionCode,installed.longVersionCode,signers,current)) { "このAPKは現在のアプリに上書き更新できません。署名またはバージョンが一致しません。" }
    }

    suspend fun installerUri(): Uri = withContext(Dispatchers.IO) {
        initialization?.join()
        lock.withLock {
            val release=requireNotNull(state.value.release)
            require(state.value.stage == ApkStage.READY) { "ダウンロード完了後に更新してください。" }
            try { verify(file(),release) } catch(e: Exception) { failure(e); throw e }
            FileProvider.getUriForFile(context,"${context.packageName}.updates",file())
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        initialization?.join(); lock.withLock { clearLocked() }
    }
    private fun clearLocked() {
        if(id > 0) manager.remove(id)
        val apk=file()
        require(!apk.exists() || apk.delete()) { "前回の更新ファイルを削除できません。" }
        val patch=patchFile()
        require(!patch.exists() || patch.delete()) { "前回の差分ファイルを削除できません。" }
        id=0; usingDelta=false; prefs.edit().clear().commit(); state.value=ApkUpdateState()
    }
    private fun failure(error: Exception, release: AppRelease? = state.value.release) {
        if(error is CancellationException) throw error
        AppLog.error("アプリ更新",error)
        state.value=state.value.copy(release=release,stage=ApkStage.ERROR,message=error.message ?: "更新ファイルを取得できませんでした。")
    }
}
