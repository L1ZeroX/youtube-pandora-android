package io.github.l1zerox.pandora

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** Only the resumed screen shows the prompt; keep pending results while another dialog or DL is active. */
@Composable
internal fun UpdatePrompt(allowed: Boolean = true) {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val foreground=lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val state by AppUpdates.state.collectAsStateWithLifecycle()
    val screen by Store.state.collectAsStateWithLifecycle()
    val extractor by ExtractorUpdates.owner(LocalContext.current).state.collectAsStateWithLifecycle()
    LaunchedEffect(foreground) {
        if(foreground) {
            AppUpdates.check()
            // One bounded retry while visible, for transient offline/startup failures.
            val checked=AppUpdates.state.first { !it.checking }
            if(checked.failed && checked.message.isEmpty()) {
                delay(30_000)
                AppUpdates.retryFailedCheck()
            }
        }
    }
    ExtractorUpdatePrompt(foreground,allowed && (extractor.updating || !state.checking && state.release == null && state.message.isEmpty()))
    if(foreground && allowed && !screen.busy && !extractor.updating && (state.release != null || state.message.isNotEmpty()))
        UpdateDialog(state,AppUpdates::dismiss)
}

@Composable
internal fun UpdateDialog(state: UpdateState, dismiss: ()->Unit) {
    val context=LocalContext.current
    var browserError by remember { mutableStateOf("") }
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }
            .onFailure { browserError="ブラウザーを開けませんでした。GitHubのリリースページから更新してください。" }
    }
    val release=state.release
    fun download(fullApk: Boolean = false) {
        if(release == null) return
        runCatching { openAppUpdate(context,if(fullApk) release.copy(delta=null) else release); dismiss() }
            .onFailure { browserError="更新画面を開けませんでした。${it.message.orEmpty()}" }
    }
    AlertDialog(onDismissRequest=dismiss,title={Text(if(release?.version?.contains("-beta.") == true) "ベータ版をダウンロード" else if(release != null) "リリース版をダウンロード" else "バージョンチェック")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(release != null) {
                Text("現在 ${BuildConfig.VERSION_NAME} → ${release.version}${if(release.preview) "（試用版）" else ""}")
                release.delta?.let { delta ->
                    Text("差分更新 約%.1f MB（通常 約%.1f MB）。利用できない場合はAPK全体を取得します。".format(delta.size/1048576.0,release.size/1048576.0),fontSize=12.sp)
                    OutlinedButton(onClick={download(fullApk=true)}) { Text("通常APKで更新") }
                }
                Text(release.notes,fontSize=13.sp)
                Text("アプリ内でAPKを取得し、Androidの更新確認画面を開きます。設定・キューは引き継ぎます。",fontSize=12.sp)
                TextButton(onClick={open(release.pageUrl)}) { Text("GitHubで更新内容を見る") }
            } else Text(state.message)
            if(browserError.isNotBlank()) Text(browserError,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={
        if(release != null) TextButton(onClick={download()}) { Text("アプリ内で更新") }
        else TextButton(onClick=dismiss) { Text("閉じる") }
    },dismissButton={ if(release != null) TextButton(onClick=dismiss) { Text("あとで") } })
}
