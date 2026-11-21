package io.github.l1zerox.pandora

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MusicShuffleDialogTest {
    @get:Rule val screen=createComposeRule()
    @Test fun albumAllAndOffAreSeparateChoicesAndCancelDoesNotChangeScope() {
        var choice: MusicShuffleScope?=null; var cancelled=false
        screen.setContent { MaterialTheme { MusicShuffleDialog({choice=it},{cancelled=true}) } }
        screen.onNodeWithText("アルバム内でシャッフル").performClick()
        screen.runOnIdle { assertEquals(MusicShuffleScope.ALBUM,choice) }
        screen.onNodeWithText("アルバム外も含めてシャッフル").performClick()
        screen.runOnIdle { assertEquals(MusicShuffleScope.ALL,choice) }
        screen.onNodeWithText("シャッフルをオフ").performClick()
        screen.runOnIdle { assertEquals(MusicShuffleScope.OFF,choice); choice=null }
        screen.onNodeWithText("キャンセル").performClick()
        screen.runOnIdle { assertTrue(cancelled); assertNull(choice) }
    }
}
