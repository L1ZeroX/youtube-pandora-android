package io.github.l1zerox.pandora

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UpdateDownloadChoiceTest {
    @get:Rule val screen=createComposeRule()
    private fun checkChoice(label: String, keepDelta: Boolean) {
        val version="0.4.25-beta.3"; val base="0.4.25-beta.2"
        val release=AppRelease(version,"$RELEASES_URL/tag/v$version",
            "$RELEASES_URL/download/v$version/YoutubePandora-Android-v$version.apk","確認用",true,1000,"sha256:"+"aa".repeat(32),
            ApkDeltaRelease(base,"$RELEASES_URL/download/v$version/${deltaAssetName(version,base)}",100,"sha256:"+"bb".repeat(32)))
        var opened: Intent?=null; var dismissed=false
        val context: Context=object: ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun startActivity(intent: Intent) { opened=intent }
        }
        screen.setContent { MaterialTheme { CompositionLocalProvider(LocalContext provides context) {
            UpdateDialog(UpdateState(release=release),dismiss={dismissed=true})
        } } }
        screen.onNodeWithText(label).performClick()
        screen.runOnIdle {
            val sent=parseApkRelease(requireNotNull(opened).getStringExtra("release"))!!
            assertEquals(release.copy(delta=if(keepDelta) release.delta else null),sent)
            assertEquals(AppUpdateActivity::class.java.name,opened!!.component!!.className)
            assertTrue(dismissed)
        }
    }
    @Test fun automaticChoiceKeepsThePatch()=checkChoice("アプリ内で更新",true)
    @Test fun fullApkChoiceKeepsIntegrityButRemovesThePatch()=checkChoice("通常APKで更新",false)
}
