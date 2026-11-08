package io.github.l1zerox.pandora

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.provider.Settings
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.update
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class BubbleOverlayTest {
    @Test fun minimizedActivityShowsOverlayPanelAndClosingBubbleKeepsDownloadState() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val automation=instrumentation.uiAutomation
        val original=Store.state.value
        val granted=Settings.canDrawOverlays(context)
        val originalFlags=automation.serviceInfo.flags
        val clipboard=context.getSystemService(ClipboardManager::class.java)
        var originalClip: ClipData? = null
        var capturedClip=false
        fun shell(command: String) { automation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
        fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo)->Boolean): AccessibilityNodeInfo? {
            if(node == null) return null
            if(predicate(node)) return node
            for(index in 0 until node.childCount) find(node.getChild(index),predicate)?.let { return it }
            return null
        }
        fun waitFor(predicate: (AccessibilityNodeInfo)->Boolean): AccessibilityNodeInfo {
            repeat(100) {
                automation.windows.forEach { window -> find(window.root,predicate)?.let {
                    if(it.isVisibleToUser) return it
                    it.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                } }
                Thread.sleep(100)
            }
            error("Overlay control was not visible")
        }
        fun touchBubble() {
            val node=waitFor { it.contentDescription?.startsWith("Pandora DL管理パネル") == true }
            val rect=Rect(); node.getBoundsInScreen(rect)
            val down=SystemClock.uptimeMillis()
            for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,rect.exactCenterX(),rect.exactCenterY(),0)
                event.source=InputDevice.SOURCE_TOUCHSCREEN
                try { assertTrue(automation.injectInputEvent(event,true)) } finally { event.recycle() }
            }
        }
        fun waitForPanelClosed() {
            repeat(100) {
                if(automation.windows.none { find(it.root) { node -> node.text?.toString() == "DL管理パネル" } != null }) return
                Thread.sleep(100)
            }
            error("Second bubble tap reopened the panel")
        }
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
            assumeTrue(Settings.canDrawOverlays(context))
            automation.serviceInfo=automation.serviceInfo.apply { flags=flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
            scenario=ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity {
                AppUpdates.dismiss()
                originalClip=clipboard.primaryClip; capturedClip=true
                clipboard.setPrimaryClip(ClipData.newPlainText("test","https://youtu.be/AbCd_12-345"))
                Store.state.value=ScreenState(preferences=original.preferences.copy(bubble=true,bubbleClipboard=true))
                BubbleRuntime.sync(it)
            }
            Thread.sleep(500)
            scenario.onActivity { it.moveTaskToBack(true) }
            val bubble=waitFor { it.contentDescription?.startsWith("Pandora DL管理パネル") == true }
            assertTrue("A passive bubble must not read the clipboard",Store.state.value.videos.isEmpty())
            bubble.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            waitFor { it.text?.toString() == "DL管理パネル" }
            waitFor { it.text?.contains("コピーしたYouTube動画を 1 件追加") == true }
            assertEquals("AbCd_12-345",youtubeVideoId(Store.state.value.videos.single().url))
            waitFor { it.text?.toString() == "閉じる" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            waitFor { it.contentDescription?.startsWith("Pandora DL管理パネル") == true }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            waitFor { it.text?.toString() == "コピーした動画は追加済みです。" }
            assertEquals(1,Store.state.value.videos.size)
            waitFor { it.text?.toString() == "閉じる" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val share=Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(context.packageName)
                .putExtra(Intent.EXTRA_TEXT,"https://youtu.be/ZyXw_98-765").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            assertEquals(ShareActivity::class.java.name,share.resolveActivity(context.packageManager)?.className)
            context.startActivity(share)
            val updated=waitFor { it.contentDescription?.startsWith("Pandora DL管理パネル") == true && it.contentDescription?.contains("ZyXw_98-765") == true }
            assertEquals(2,Store.state.value.videos.size)
            assertFalse("Sharing in bubble mode must not open MainActivity",BubbleRuntime.foreground.value)
            assertTrue("Sharing must not open the DL panel",automation.windows.none { find(it.root) { node -> node.text?.toString() == "DL管理パネル" } != null })
            updated.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            waitFor { it.text?.toString() == "MP3 音声" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            waitFor { it.text?.toString() == "✓ MP3 音声" }
            assertEquals("MP3",Store.state.value.preferences.format)
            waitFor { it.contentDescription?.toString() == "キューから削除: https://youtu.be/ZyXw_98-765" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            waitFor { it.text?.toString()?.startsWith("キュー 1 件") == true }
            assertEquals(1,Store.state.value.videos.size)
            Store.state.update { it.copy(busy=true,operation="DOWNLOAD",progress=.42f,speed="1.0 MiB/s",eta=58.0) }
            waitFor { it.text?.contains("42%") == true && it.text?.contains("1.0 MiB/s") == true }
            assertFalse(waitFor { it.text?.toString() == "MP4 動画" }.isEnabled)
            assertFalse(waitFor { it.contentDescription?.startsWith("キューから削除:") == true }.isEnabled)
            // Accessibility ACTION_CLICK alone bypasses ACTION_OUTSIDE and misses this race.
            val downloading=Store.state.value
            repeat(3) {
                touchBubble(); waitForPanelClosed()
                waitFor { it.contentDescription?.startsWith("Pandora DL管理パネルを開く") == true }
                val after=Store.state.value
                assertTrue(after.busy); assertEquals(downloading.operation,after.operation)
                assertEquals(downloading.paused,after.paused); assertEquals(downloading.progress,after.progress)
                assertTrue(after.preferences.bubble)
                touchBubble(); waitFor { it.text?.toString() == "DL管理パネル" }
                waitFor { it.contentDescription?.startsWith("Pandora DL管理パネルを閉じる") == true }
            }
            waitFor { it.text?.toString() == "バブルを終了" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Thread.sleep(500)
            assertTrue("Closing the bubble must not cancel the download",Store.state.value.busy)
            assertFalse(Store.state.value.preferences.bubble)
        } finally {
            context.stopService(Intent(context,BubbleService::class.java))
            scenario?.close()
            instrumentation.runOnMainSync {
                if(capturedClip) originalClip?.let(clipboard::setPrimaryClip) ?: clipboard.clearPrimaryClip()
            }
            Store.state.value=original; Store.save()
            automation.serviceInfo=automation.serviceInfo.apply { flags=originalFlags }
            if(!granted) shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW default")
        }
    }
}
