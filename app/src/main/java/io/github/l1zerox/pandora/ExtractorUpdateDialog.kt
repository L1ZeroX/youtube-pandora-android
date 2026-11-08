package io.github.l1zerox.pandora

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
internal fun ExtractorUpdatePrompt(foreground: Boolean, allowed: Boolean) {
    val context=LocalContext.current
    val checker=remember(context) { ExtractorUpdates.owner(context) }
    val state by checker.state.collectAsStateWithLifecycle()
    val screen by Store.state.collectAsStateWithLifecycle()
    LaunchedEffect(foreground) {
        if(foreground) {
            checker.check()
            delay(61_000)
            checker.check()
        }
    }
    if(foreground && allowed && (!screen.busy || screen.operation == "UPDATE") &&
        (state.latest != null || state.updating || state.message.isNotEmpty())) {
        AlertDialog(onDismissRequest={checker.dismiss()},title={Text(if(state.updating) "yt-dlpを更新中" else "yt-dlpの更新")},text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(state.latest != null) Text("現在 ${state.current} → ${state.latest}")
                if(state.updating) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("アプリ内で更新しています…")
                } else if(state.latest != null) Text("新しいyt-dlpが利用できます。更新すると動画取得の修正が反映されます。")
                if(state.message.isNotBlank()) Text(state.message)
            }
        },confirmButton={
            if(!state.updating) TextButton(enabled=!state.checking,onClick={
                if(state.latest != null) ExtractorUpdates.start(context) else checker.dismiss()
            }) { Text(if(state.latest != null) "今すぐ更新" else "閉じる") }
        },dismissButton={if(!state.updating) {
            if(state.latest != null) TextButton(onClick=checker::dismiss) { Text("あとで") }
            else TextButton(enabled=!state.checking,onClick={checker.check(true)}) { Text(if(state.checking) "確認中…" else "再確認") }
        }})
    }
}
