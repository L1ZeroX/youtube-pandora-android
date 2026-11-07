package io.github.l1zerox.pandora

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import kotlinx.coroutines.flow.update
import org.junit.Rule
import org.junit.Test
import java.io.File

class ScreenTest {
    @get:Rule val screen = createAndroidComposeRule<MainActivity>()
    @Test fun durationUpdatesInsideAnAlreadyOpenVideoPreview() {
        val original=Store.state.value
        val video=Video("duration-test","https://example.com/duration","Duration test","Example")
        try {
            screen.runOnIdle { AppUpdates.dismiss(); Store.state.value=ScreenState(videos=listOf(video)) }
            screen.onNodeWithText("Duration test").performClick()
            screen.onNodeWithText("動画情報を再取得").assertIsDisplayed()
            screen.runOnIdle { Store.edit(video.key) { it.copy(duration=153.0) } }
            screen.onNodeWithText("Example · 2:33 · 待機中").assertIsDisplayed()
            screen.onNodeWithText("動画情報を再取得").assertDoesNotExist()
        } finally { screen.runOnIdle { Store.state.value=original; Store.save() } }
    }
    @Test fun mp3ArtworkChoicePersistsAndLocksDuringDownload() {
        val original=Store.state.value
        try {
            screen.runOnIdle { AppUpdates.dismiss(); Store.state.value=ScreenState(preferences=Preferences(format="MP3")) }
            screen.onNodeWithText("アートワークなし").performScrollTo().performClick()
            screen.onNodeWithText("アートワークなし").assertIsSelected()
            screen.runOnIdle { org.junit.Assert.assertEquals("なし",Store.state.value.preferences.artworkMode) }
            screen.onNodeWithText("サムネイル").performScrollTo().performClick()
            screen.onNodeWithText("サムネイル").assertIsSelected()
            screen.runOnIdle {
                org.junit.Assert.assertEquals("サムネイル",Store.state.value.preferences.artworkMode)
                Store.state.update { it.copy(busy=true,operation="DOWNLOAD") }
            }
            screen.onNodeWithText("ギャラリーから選ぶ").assertIsNotEnabled()
            screen.onNodeWithText("サムネイル").assertIsNotEnabled()
        } finally { screen.runOnIdle { Store.state.value=original; Store.save() } }
    }
    @Test fun promotedFeaturesAreAvailableInTheDefaultReleaseChannel() {
        val original=Store.state.value
        try {
            val service=screen.activity.packageManager.getServiceInfo(
                android.content.ComponentName(screen.activity,BubbleService::class.java),0)
            org.junit.Assert.assertTrue(service.enabled)
            screen.runOnIdle {
                AppUpdates.dismiss()
                Store.state.value=ScreenState(busy=true,operation="DOWNLOAD")
            }
            screen.onNodeWithText("一時停止").assertIsEnabled()
            screen.runOnIdle { Store.state.update { it.copy(paused=true) } }
            screen.onNodeWithText("再開").assertIsEnabled()
            screen.onNodeWithText("キャンセル").assertIsEnabled()
            screen.runOnIdle { Store.state.value=ScreenState() }
            screen.onNodeWithText("環境設定").performClick()
            screen.onNodeWithText("最小化するとバブルを表示").performScrollTo().assertIsDisplayed()
        } finally { screen.runOnIdle { Store.state.value=original; Store.save() } }
    }
    @Test fun backgroundPreviewReflectsAdjustmentsAndPreservesSettingsOnClose() {
        val original=Store.state.value
        val fixture=File(screen.activity.filesDir,"preview-test.png")
        val image=Bitmap.createBitmap(60,120,Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.BLUE)
        fixture.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        image.recycle()
        try {
            screen.runOnIdle {
                AppUpdates.dismiss()
                Store.preferences(original.preferences.copy(wallpaper=fixture.absolutePath))
            }
            screen.onNodeWithText("環境設定").performClick()
            screen.onNodeWithText("背景をプレビュー・調整").performScrollTo().performClick()
            screen.onNodeWithText("仕上がりプレビュー").assertIsDisplayed()
            screen.waitUntil(5000) {
                screen.onAllNodes(hasTestTag("background-opacity") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            screen.onNodeWithTag("background-opacity").performSemanticsAction(SemanticsActions.SetProgress) { it(.86f) }
            screen.onNodeWithTag("background-glass").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(.62f) }
            screen.onNodeWithTag("background-blur").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(16f) }
            screen.onNodeWithTag("background-preview").assertIsDisplayed()
                .assertContentDescriptionEquals("背景の表示サンプル。透明度 86%、パネル透過率 62%、ぼかし 16")
            screen.onNodeWithText("完了").performClick()
            screen.onNodeWithText("背景をプレビュー・調整").assertExists()
            screen.onNodeWithText("背景をプレビュー・調整").performScrollTo().performClick()
            screen.onNodeWithTag("background-preview").assertContentDescriptionEquals(
                "背景の表示サンプル。透明度 86%、パネル透過率 62%、ぼかし 16")
            screen.onNodeWithText("背景を解除").performScrollTo().performClick()
            screen.waitUntil(5000) {
                screen.onAllNodes(hasTestTag("background-opacity") and isNotEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            screen.onNodeWithTag("background-preview").assertIsDisplayed()
            screen.onNodeWithText("完了").performClick()
            screen.onNodeWithText("閉じる").performClick()
        } finally {
            screen.runOnIdle { Store.state.value=original; Store.save() }
            fixture.delete()
        }
    }
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
