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
                val targetBeta = release.version.contains("-beta.")
                Text(when {
                    BuildConfig.BETA && !targetBeta -> "リリース版（Youtube Pandora!）のAPKをダウンロードして開いてください。ベータ版とは別アプリです。インストール後はリリース版を起動してください。既存のリリース版の設定・一覧は引き継がれますが、ベータ版の設定・一覧は移行されません。"
                    !BuildConfig.BETA && targetBeta -> "ベータ版は通常版と別にインストールされます。設定・一覧も別管理です。"
                    else -> "APKをダウンロードして開き、アプリを削除せず上書き更新してください。"
                },fontSize=12.sp)
                TextButton(onClick={open(release.pageUrl)}) { Text("GitHubで更新内容を見る") }
            } else Text(state.message)
            if(browserError.isNotBlank()) Text(browserError,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={
        if(release != null) TextButton(onClick={open(release.downloadUrl)}) { Text("APKをダウンロード") }
        else TextButton(onClick=dismiss) { Text("閉じる") }
    },dismissButton={ if(release != null) TextButton(onClick=dismiss) { Text("あとで") } })
}
