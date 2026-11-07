package io.github.l1zerox.pandora

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.update
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class BubbleOverlayTest {
    @Test fun minimizedActivityShowsOverlayPanelAndClosingBubbleKeepsDownloadState() {
        assumeTrue(BuildConfig.BETA)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val automation=instrumentation.uiAutomation
        val original=Store.state.value
        val granted=Settings.canDrawOverlays(context)
        val originalFlags=automation.serviceInfo.flags
        fun shell(command: String) { automation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
        fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo)->Boolean): AccessibilityNodeInfo? {
            if(node == null) return null
            if(predicate(node)) return node
            for(index in 0 until node.childCount) find(node.getChild(index),predicate)?.let { return it }
            return null
        }
        fun waitFor(predicate: (AccessibilityNodeInfo)->Boolean): AccessibilityNodeInfo {
            repeat(100) {
                automation.windows.forEach { window -> find(window.root,predicate)?.let { return it } }
                Thread.sleep(100)
            }
            error("Overlay control was not visible")
        }
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
            assumeTrue(Settings.canDrawOverlays(context))
            automation.serviceInfo=automation.serviceInfo.apply { flags=flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
            scenario=ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity {
                AppUpdates.dismiss()
                Store.preferences(Store.state.value.preferences.copy(bubble=true))
                BubbleRuntime.sync(it)
            }
            Thread.sleep(500)
            scenario.onActivity { it.moveTaskToBack(true) }
            waitFor { it.contentDescription?.startsWith("Pandora DL管理パネル") == true }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            waitFor { it.text?.toString() == "DL管理パネル" }
            Store.state.update { it.copy(busy=true,operation="DOWNLOAD",progress=.42f,speed="1.0 MiB/s",eta=58.0) }
            waitFor { it.text?.contains("42%") == true && it.text?.contains("1.0 MiB/s") == true }
            waitFor { it.text?.toString() == "バブルを終了" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Thread.sleep(500)
            assertTrue("Closing the bubble must not cancel the download",Store.state.value.busy)
            assertFalse(Store.state.value.preferences.bubble)
        } finally {
            context.stopService(Intent(context,BubbleService::class.java))
            scenario?.close()
            Store.state.value=original; Store.save()
            automation.serviceInfo=automation.serviceInfo.apply { flags=originalFlags }
            if(!granted) shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW default")
        }
    }
}
