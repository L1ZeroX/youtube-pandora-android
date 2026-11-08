package io.github.l1zerox.pandora

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SaveRangeDialogTest {
    @get:Rule val screen=createComposeRule()
    @Test fun acceptsTimeRangeAndRejectsReversedTimes() {
        var saved: SaveRange?=null
        screen.setContent { MaterialTheme { SaveRangeDialog(SaveRange(),true,{saved=it},{}) } }
        screen.onNodeWithContentDescription("保存範囲を指定").performClick()
        screen.onNodeWithText("開始時刻").performTextReplacement("1:30")
        screen.onNodeWithText("終了時刻").performTextReplacement("1:00")
        screen.onNodeWithText("適用").performClick()
        screen.onNodeWithText("終了時刻は開始時刻より後にしてください。").assertIsDisplayed()
        screen.runOnIdle { assertNull(saved) }
        screen.onNodeWithText("終了時刻").performTextReplacement("2:00")
        screen.onNodeWithText("適用").performClick()
        screen.runOnIdle { assertEquals(SaveRange(true,90,120),saved) }
    }
    @Test fun blankEndMeansToEndAndTurningOffPreservesExistingTimes() {
        var saved: SaveRange?=null
        screen.setContent { MaterialTheme { SaveRangeDialog(SaveRange(true,30,90),true,{saved=it},{}) } }
        screen.onNodeWithText("終了時刻").performTextReplacement("")
        screen.onNodeWithText("適用").performClick()
        screen.runOnIdle { assertEquals(SaveRange(true,30),saved) }
        screen.onNodeWithContentDescription("保存範囲を指定").performClick()
        screen.onNodeWithText("適用").performClick()
        screen.runOnIdle { assertEquals(SaveRange(false,30,90),saved) }
    }
    @Test fun busyDownloadLocksEditingAndCancelNeverApplies() {
        var applied=false; var cancelled=false
        screen.setContent { MaterialTheme { SaveRangeDialog(SaveRange(true,30,90),false,{applied=true},{cancelled=true}) } }
        screen.onNodeWithContentDescription("保存範囲を指定").assertIsNotEnabled()
        screen.onNodeWithText("開始時刻").assertIsNotEnabled()
        screen.onNodeWithText("終了時刻").assertIsNotEnabled()
        screen.onNodeWithText("適用").assertIsNotEnabled()
        screen.onNodeWithText("キャンセル").performClick()
        screen.runOnIdle { assertFalse(applied); assertTrue(cancelled) }
    }
}
