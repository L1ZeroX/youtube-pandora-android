package io.github.l1zerox.pandora

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AudioLanguageDeviceTest {
    @Test fun preferenceAndDetectionResultSurviveReloadAndOldPreferencesDefaultToOff() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val original=Store.state.value
        try {
            Store.state.value=ScreenState(preferences=original.preferences.copy(preferJapaneseAudio=true),
                videos=listOf(Video("fixture","https://example.com/fixture","fixture",audioInfo="日本語音声を使用")))
            Store.save(); Store.init(context)
            assertTrue(Store.state.value.preferences.preferJapaneseAudio)
            assertEquals("日本語音声を使用",Store.state.value.videos.single().audioInfo)
            val prefs=context.getSharedPreferences("pandora",0)
            val json=JSONObject(prefs.getString("preferences","{}")!!).apply { remove("preferJapaneseAudio") }
            prefs.edit().putString("preferences",json.toString()).commit()
            Store.init(context)
            assertFalse(Store.state.value.preferences.preferJapaneseAudio)
        } finally { Store.state.value=original; Store.save() }
    }
}
