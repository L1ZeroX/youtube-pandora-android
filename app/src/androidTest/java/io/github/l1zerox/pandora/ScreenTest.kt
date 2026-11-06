package io.github.l1zerox.pandora

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class ScreenTest {
    @get:Rule val screen = createAndroidComposeRule<MainActivity>()
    @Test fun logDialogShowsRecordedFailure() {
        AppLog.error("テスト",IllegalStateException("診断用エラー"))
        screen.waitUntil(5000) { AppLog.state.value.entries.any { it.message.contains("診断用エラー") } }
        screen.onNodeWithText("ログ (",substring=true).performClick()
        screen.onNodeWithText("処理ログ").assertIsDisplayed()
        screen.onNodeWithText("診断用エラー",substring=true).assertExists()
        screen.onNodeWithText("コピー").assertIsDisplayed()
        screen.onNodeWithText("共有").assertIsDisplayed()
        screen.onNodeWithText("閉じる").performClick()
    }
    @Test fun opensSettingsAndQualityMenu() {
        screen.onNodeWithText("リンクを追加").assertIsDisplayed()
        screen.onNodeWithText("環境設定").performClick()
        screen.onNodeWithText("ダークモード").assertIsDisplayed()
        capture("settings.png")
        screen.onNodeWithText("閉じる").performClick()
        screen.onNodeWithText("1080p以下",substring=true).performScrollTo().performClick()
        screen.onNodeWithText("    720p以下").performClick()
        screen.onNodeWithText("720p以下",substring=true).assertIsDisplayed()
        capture("screen.png")
    }
    private fun capture(name: String) {
        screen.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null),name).outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG,100,it)
        }
        image.recycle()
    }
}
