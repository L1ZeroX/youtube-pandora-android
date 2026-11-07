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
    if(foreground && allowed && !screen.busy && (state.release != null || state.message.isNotEmpty()))
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
    AlertDialog(onDismissRequest=dismiss,title={Text(if(release?.version?.contains("-beta.") == true) "ベータ版をダウンロード" else if(release != null) "リリース版をダウンロード" else "バージョンチェック")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(release != null) {
                Text("現在 ${BuildConfig.VERSION_NAME} → ${release.version}${if(release.preview) "（試用版）" else ""}")
                Text(release.notes,fontSize=13.sp)
                Text("統合版は通常版・ベータ版とも同じアプリとして上書きインストールし、設定・キューを引き継ぎます。ダウンロード処理を終了してからAPKを開いてください。リリース版v0.4.0以降でもバブルを利用できます。",fontSize=12.sp)
                TextButton(onClick={open(release.pageUrl)}) { Text("GitHubで更新内容を見る") }
            } else Text(state.message)
            if(browserError.isNotBlank()) Text(browserError,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={
        if(release != null) TextButton(onClick={open(release.downloadUrl)}) { Text("APKをダウンロード") }
        else TextButton(onClick=dismiss) { Text("閉じる") }
    },dismissButton={ if(release != null) TextButton(onClick=dismiss) { Text("あとで") } })
}
