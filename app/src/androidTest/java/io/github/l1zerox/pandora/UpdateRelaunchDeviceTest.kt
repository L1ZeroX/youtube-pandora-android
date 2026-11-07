package io.github.l1zerox.pandora

import android.content.ComponentName
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class UpdateRelaunchDeviceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun replacedReceiverIsPrivateAndNotificationOpensMainDirectly() {
        val component=ComponentName(context,UpdateReplacedReceiver::class.java)
        assertFalse(context.packageManager.getReceiverInfo(component,0).exported)
        val intent=UpdateRelaunch.launchIntent(context)
        assertEquals(ComponentName(context,MainActivity::class.java),intent.component)
        assertEquals(Intent.ACTION_MAIN,intent.action)
        assertTrue(intent.categories.contains(Intent.CATEGORY_LAUNCHER))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
    }

    @Test fun savedRequestIsConsumedOnceAndCancellationPreventsReopening() {
        val prefs=context.getSharedPreferences("update_relaunch_test",0)
        val now=System.currentTimeMillis()
        val request=UpdateRelaunchRequest("0.4.23",43,now)
        try {
            val firstProcess=UpdateRelaunchStore(prefs)
            firstProcess.arm(request)
            val replacedProcess=UpdateRelaunchStore(prefs)
            assertEquals(request,replacedProcess.read())
            assertTrue(replacedProcess.consume("0.4.23",44,now+1000))
            assertFalse(UpdateRelaunchStore(prefs).consume("0.4.23",44,now+1001))
            firstProcess.arm(request); firstProcess.clear()
            assertFalse(replacedProcess.consume("0.4.23",44,now+1000))
            firstProcess.arm(request)
            assertFalse(replacedProcess.consume("0.4.24",45,now+1000))
            assertNull(replacedProcess.read())
            firstProcess.arm(request)
            assertFalse(replacedProcess.consume("0.4.23",44,now+UPDATE_RELAUNCH_WINDOW_MS+1))
            assertNull(replacedProcess.read())
        } finally { prefs.edit().clear().commit() }
    }
}
