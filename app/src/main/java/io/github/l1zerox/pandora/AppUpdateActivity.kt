package io.github.l1zerox.pandora

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

internal fun openAppUpdate(context: Context, release: AppRelease? = null) {
    context.startActivity(Intent(context,AppUpdateActivity::class.java).apply {
        release?.let { putExtra("release",it.updateJson()) }
    })
}

class AppUpdateActivity : ComponentActivity() {
    private var autoInstall=false
    private var expectedVersion: String? = null
    private var externalStep by mutableStateOf(false)
    private var preparingInstall by mutableStateOf(false)
    private val installBusy get()=externalStep || preparingInstall
    private var message by mutableStateOf("")
    private fun external(value: Boolean) { externalStep=value; BubbleRuntime.hideForUpdate.value=value }
    private val permission=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        external(false)
        if(packageManager.canRequestPackageInstalls()) launchInstaller()
        else message="インストールは許可されていません。「更新する」からもう一度設定できます。"
    }
    private val installer=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        external(false)
        if(result.resultCode == RESULT_OK) lifecycleScope.launch {
            runCatching { ApkUpdates.clear() }.onFailure { AppLog.error("APK整理",it) }
            message="更新が完了しました。"
        } else message="更新を完了していません。「更新する」から再度開けます。"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        val requested=parseApkRelease(intent.getStringExtra("release"))
        expectedVersion=requested?.version
        autoInstall=savedInstanceState?.getBoolean("autoInstall") ?: (requested != null)
        external(savedInstanceState?.getBoolean("externalStep") ?: false)
        if(requested != null && (savedInstanceState == null || ApkUpdates.state.value.release == null)) ApkUpdates.start(requested)
        setContent {
            val update by ApkUpdates.state.collectAsStateWithLifecycle()
            val screen by Store.state.collectAsStateWithLifecycle()
            MaterialTheme(colorScheme=if(screen.preferences.dark) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text("アプリを更新",style=MaterialTheme.typography.headlineMedium)
                        Text(update.release?.let { "現在 ${BuildConfig.VERSION_NAME} → ${it.version}" } ?: "更新ファイルはありません。")
                        when(update.stage) {
                            ApkStage.DOWNLOADING -> {
                                val fraction=if(update.total > 0) (update.bytes.toFloat()/update.total).coerceIn(0f,1f) else null
                                if(fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                                else LinearProgressIndicator(progress={fraction},modifier=Modifier.fillMaxWidth())
                                Text(update.message)
                                Text("%.1f MB".format(update.bytes/1048576.0) + if(update.total > 0)
                                    " / %.1f MB · %d%%".format(update.total/1048576.0,((fraction ?: 0f)*100).toInt()) else "")
                            }
                            ApkStage.VERIFYING -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("APKを確認しています…") }
                            ApkStage.READY -> Text("ダウンロード完了。Androidの確認画面で「更新」を押してください。")
                            ApkStage.ERROR -> Text(update.message,color=MaterialTheme.colorScheme.error)
                            ApkStage.IDLE -> Unit
                        }
                        Text("初回はこのアプリからのインストールを許可します。更新用APKはアプリ専用領域に保存し、更新後の起動時に整理します。")
                        if(message.isNotBlank()) Text(message)
                        if(screen.busy) Text("動画のダウンロードが終わってから更新できます。")
                        if(update.stage == ApkStage.READY) Button(onClick={autoInstall=false; requestInstall()},enabled=!installBusy && !screen.busy) { Text("更新する") }
                        if(update.stage == ApkStage.ERROR && update.release != null) Button(onClick={
                            autoInstall=true; message=""; ApkUpdates.start(update.release!!)
                        },enabled=!installBusy) { Text("再ダウンロード") }
                        if(update.release != null) OutlinedButton(onClick={
                            autoInstall=false
                            lifecycleScope.launch { runCatching { ApkUpdates.clear() }.onSuccess { finish() }.onFailure { message=it.message.orEmpty() } }
                        },enabled=!installBusy && update.stage != ApkStage.VERIFYING) {
                            Text(if(update.working) "ダウンロードをキャンセル" else "更新ファイルを削除")
                        }
                        TextButton(onClick={finish()},enabled=!installBusy) { Text("戻る") }
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while(true) { if(ApkUpdates.state.value.working) ApkUpdates.refresh(); delay(750) }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                ApkUpdates.state.collect {
                    if(it.stage == ApkStage.READY && autoInstall && !installBusy && !Store.state.value.busy &&
                        (expectedVersion == null || it.release?.version == expectedVersion)) {
                        autoInstall=false; requestInstall()
                    }
                }
            }
        }
    }

    private fun requestInstall() {
        if(installBusy || Store.state.value.busy) return
        if(expectedVersion != null && ApkUpdates.state.value.release?.version != expectedVersion) return
        message=""
        if(packageManager.canRequestPackageInstalls()) launchInstaller()
        else try {
            external(true)
            permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:$packageName")))
        } catch(e: Exception) { external(false); message="インストール許可の画面を開けませんでした。${e.message.orEmpty()}" }
    }

    @Suppress("DEPRECATION")
    private fun launchInstaller() {
        if(installBusy || Store.state.value.busy) return
        preparingInstall=true
        lifecycleScope.launch {
            try {
                val uri=ApkUpdates.installerUri()
                lifecycle.withResumed {
                    if(!Store.state.value.busy) {
                        external(true)
                        installer.launch(Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,"application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT,true)
                            .apply { clipData=ClipData.newRawUri("APK",uri) })
                    }
                }
            } catch(e: Exception) {
                external(false)
                if(e is CancellationException) throw e
                message="更新画面を開けませんでした。${e.message.orEmpty()}"
            } finally { preparingInstall=false }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("autoInstall",autoInstall); outState.putBoolean("externalStep",externalStep)
        super.onSaveInstanceState(outState)
    }
    override fun onStart() { super.onStart(); BubbleRuntime.enter(this) }
    override fun onStop() { super.onStop(); BubbleRuntime.leave(this) }
    override fun onDestroy() {
        if(!isChangingConfigurations) BubbleRuntime.hideForUpdate.value=false
        super.onDestroy()
    }
}
