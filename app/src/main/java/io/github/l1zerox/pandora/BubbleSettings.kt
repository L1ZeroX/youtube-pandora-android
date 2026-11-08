package io.github.l1zerox.pandora

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun BubbleSettings(p: Preferences) {
    val context=LocalContext.current
    val permission by BubbleRuntime.permission.collectAsStateWithLifecycle()
    val error by BubbleRuntime.error.collectAsStateWithLifecycle()
    fun requestOverlay() {
        if(Settings.canDrawOverlays(context)) BubbleRuntime.sync(context)
        else runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:${context.packageName}"))) }
            .onFailure { BubbleRuntime.error.value="Androidの設定から「他のアプリの上に表示」を許可してください。" }
    }
    val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { requestOverlay() }
    fun requestBubblePermissions() {
        if(Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else requestOverlay()
    }
    Text("バブル",fontWeight=FontWeight.Bold)
    Row(verticalAlignment=Alignment.CenterVertically) {
        Text("最小化するとバブルを表示",Modifier.weight(1f))
        Switch(p.bubble,{ enabled ->
            BubbleRuntime.error.value=""
            Store.preferences(Store.state.value.preferences.copy(bubble=enabled))
            if(enabled) requestBubblePermissions() else BubbleRuntime.sync(context)
        })
    }
    Text("タップで開く・閉じる。ドラッグで移動。",fontSize=12.sp)
    Row(verticalAlignment=Alignment.CenterVertically) {
        Text("コピーしたYouTube URLを追加",Modifier.weight(1f))
        Switch(p.bubbleClipboard,{ Store.preferences(Store.state.value.preferences.copy(bubbleClipboard=it)) })
    }
    Text("URLをコピー後、バブルを開くと追加します。",fontSize=12.sp)
    if(p.bubble && !permission) {
        Text("「他のアプリの上に表示」を許可してください。",fontSize=12.sp)
        OutlinedButton(onClick={requestBubblePermissions()}) { Text("表示許可の設定を開く") }
    }
    if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
}
