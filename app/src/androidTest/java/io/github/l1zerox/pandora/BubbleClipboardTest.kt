package io.github.l1zerox.pandora

import android.content.ClipData
import android.content.ClipboardManager
import android.os.PersistableBundle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class BubbleClipboardTest {
    @Test fun focusedPanelAddsOnlyYoutubeVideosAndStopsListeningWhenClosed() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val manager=context.getSystemService(ClipboardManager::class.java)
        val original=Store.state.value
        var originalClip: ClipData? = null
        var gate: BubbleClipboard? = null
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            var focused=false
            repeat(50) {
                scenario.onActivity { activity -> focused=activity.hasWindowFocus(); AppUpdates.dismiss() }
                if(!focused) Thread.sleep(100)
            }
            assertTrue("Activity must own focus for Android clipboard access",focused)
            scenario.onActivity {
                originalClip=manager.primaryClip
                Store.state.value=ScreenState()
                var enabled=false
                val reader=BubbleClipboard(context,{ enabled }) {}
                gate=reader
                manager.setPrimaryClip(ClipData.newPlainText("test","https://youtu.be/AbCd_12-345"))
                reader.focusChanged(true)
                assertTrue(Store.state.value.videos.isEmpty())
                enabled=true; reader.focusChanged(true)
                assertEquals(1,Store.state.value.videos.size)
                reader.close()
                manager.setPrimaryClip(ClipData.newPlainText("test","https://www.youtube.com/watch?v=AbCd_12-345&t=3"))
                reader.focusChanged(true)
                assertEquals(1,Store.state.value.videos.size)
                reader.close()
                manager.setPrimaryClip(ClipData.newPlainText("test","https://youtube.com.evil.test/watch?v=ZyXw_98-765"))
                reader.focusChanged(true)
                assertEquals(1,Store.state.value.videos.size)
                reader.close()
                val sensitive=ClipData.newPlainText("test","https://youtu.be/ZyXw_98-765")
                sensitive.description.extras=PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE",true) }
                manager.setPrimaryClip(sensitive); reader.focusChanged(true)
                assertEquals(1,Store.state.value.videos.size)
                reader.focusChanged(false)
                manager.setPrimaryClip(ClipData.newPlainText("test","https://youtu.be/ZyXw_98-765"))
            }
            instrumentation.waitForIdleSync()
            assertEquals("Closed panel must not consume clipboard changes",1,Store.state.value.videos.size)
            scenario.onActivity {
                gate!!.focusChanged(true)
                assertEquals(2,Store.state.value.videos.size)
                val added=Store.state.value.videos.last()
                Store.removeQueueVideo(added.key)
                gate!!.focusChanged(false); gate!!.focusChanged(true)
                assertEquals("Deleting an item must not reimport the same clipboard on reopen",1,Store.state.value.videos.size)
            }
        } finally {
            scenario.onActivity {
                gate?.close()
                originalClip?.let(manager::setPrimaryClip) ?: manager.clearPrimaryClip()
                Store.state.value=original; Store.save()
            }
            scenario.close()
        }
    }
}
