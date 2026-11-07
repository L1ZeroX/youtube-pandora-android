package io.github.l1zerox.pandora

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class UpdatePromptTest {
    @get:Rule val screen=createComposeRule()
    @Test fun availableUpdateWaitsForDownloadsAndOtherDialogsThenCanBeDismissed() {
        val original=Store.state.value
        val originalUpdate=AppUpdates.state.value
        val allowed=mutableStateOf(false)
        val release=AppRelease("99.0.0","$RELEASES_URL/tag/v99.0.0",
            "$RELEASES_URL/download/v99.0.0/YoutubePandora-Android-v99.0.0.apk","表示確認用の更新",false)
        try {
            // Pending results also prevent this isolated UI test from making a live network request.
            AppUpdates.state.value=UpdateState(release=release)
            Store.state.value=original.copy(busy=true)
            screen.setContent { MaterialTheme { UpdatePrompt(allowed.value) } }
            screen.onNodeWithText("アプリ内で更新").assertDoesNotExist()
            screen.runOnIdle { allowed.value=true }
            screen.onNodeWithText("アプリ内で更新").assertDoesNotExist()
            screen.runOnIdle { Store.state.value=original.copy(busy=false) }
            screen.onNodeWithText("表示確認用の更新").assertIsDisplayed()
            screen.onNodeWithText("アプリ内で更新").assertIsDisplayed()
            screen.onNodeWithText("あとで").performClick()
            screen.onNodeWithText("アプリ内で更新").assertDoesNotExist()
        } finally { screen.runOnIdle { Store.state.value=original; AppUpdates.state.value=originalUpdate } }
    }
}
