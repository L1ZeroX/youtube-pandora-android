package io.github.l1zerox.pandora

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import kotlinx.coroutines.flow.update
import org.junit.Rule
import org.junit.Test
import java.io.File

class ScreenTest {
    @get:Rule val screen = createAndroidComposeRule<MainActivity>()
    @Test fun addLinkPastesAndPreservesExistingInput() {
        screen.runOnIdle {
            AppUpdates.dismiss()
            Store.state.update { it.copy(busy=false) }
            val clipboard=screen.activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("link","https://b.test\nhttps://c.test"))
        }
        screen.onNode(hasSetTextAction()).performTextReplacement("https://a.test")
        screen.onNodeWithText("リンクを追加").performClick()
        screen.onNode(hasSetTextAction()).assertTextEquals("https://a.test\nhttps://b.test\nhttps://c.test")
        screen.runOnIdle { Store.state.update { it.copy(busy=true) } }
        screen.onNodeWithText("リンクを追加").assertIsNotEnabled()
        screen.runOnIdle { Store.state.update { it.copy(busy=false) } }
    }
    @Test fun progressSurvivesKnownToUnknownBeforeNextFrame() {
        val original=Store.state.value
        try {
            screen.runOnIdle { AppUpdates.dismiss(); Store.state.update { it.copy(busy=true,progress=.75f) } }
            screen.onNodeWithTag("download-progress").assertExists()
            screen.mainClock.autoAdvance=false
            // Force semantics reads while the old progress callback may still be installed.
            for(value in listOf(null,.5f,Float.NaN,1f,null,.2f)) {
                screen.runOnIdle { Store.state.update { it.copy(progress=value) } }
                screen.onNodeWithTag("download-progress").fetchSemanticsNode().config
                screen.mainClock.advanceTimeByFrame()
                screen.waitForIdle()
                val expected=if(value == null || !value.isFinite()) ProgressBarRangeInfo.Indeterminate
                    else ProgressBarRangeInfo(value,0f..1f)
                screen.onNodeWithTag("download-progress").assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,expected))
            }
        } finally {
            screen.mainClock.autoAdvance=true
            screen.runOnIdle { Store.state.value=original }
        }
    }
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
