package io.github.l1zerox.pandora

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SaveRangeDialogTest {
    @get:Rule val screen=createComposeRule()
    @Test fun barAndTimeFieldsStayInSyncBeforeApply() {
        var saved: SaveRange?=null
        val video=Video("one","https://example.com/1","One",duration=180.0)
        screen.setContent { MaterialTheme { SaveRangeDialog(SaveRange(true,30,90),true,{saved=it},{},listOf(video)) } }
        screen.onNodeWithText("開始時刻").performScrollTo().performTextReplacement("0:40")
        screen.onNodeWithText("終了時刻").performScrollTo().performTextReplacement("2:00")
        screen.onNodeWithTag("save-range-slider").performScrollTo()
        screen.onNodeWithText("開始 0:40").assertIsDisplayed()
        screen.onNodeWithText("終了 2:00").assertIsDisplayed()
        val handles=screen.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress),useUnmergedTree=true)
        handles[0].performSemanticsAction(SemanticsActions.SetProgress) { it(50f) }
        handles[1].performSemanticsAction(SemanticsActions.SetProgress) { it(100f) }
        screen.onNodeWithText("開始時刻").performScrollTo().assertTextContains("0:50")
        screen.onNodeWithText("終了時刻").performScrollTo().assertTextContains("1:40")
        screen.onNodeWithText("適用").performClick()
        screen.runOnIdle { assertEquals(SaveRange(true,50,100),saved) }
    }
    @Test fun referenceSwitchDoesNotRewriteTypedTimes() {
        var saved: SaveRange?=null
        val one=Video("one","https://example.com/1","One",duration=180.0)
        val two=one.copy(key="two",title="Two",duration=60.0)
        screen.setContent { MaterialTheme { SaveRangeDialog(SaveRange(true,30,90),true,{saved=it},{},listOf(one,two)) } }
        screen.onNodeWithText("基準: One ▾").performClick()
        screen.onNodeWithText("Two · 1:00").performClick()
        screen.onNodeWithText("終了 1:00").assertIsDisplayed()
        screen.onNodeWithText("終了時刻").performScrollTo().assertTextContains("1:30")
        screen.onNodeWithText("適用").performClick()
        screen.runOnIdle { assertEquals(SaveRange(true,30,90),saved) }
    }
    @Test fun unknownDurationKeepsManualTimeInputAvailable() {
        var saved: SaveRange?=null
        val unknown=Video("unknown","https://example.com/1","Unknown")
        screen.setContent { MaterialTheme { SaveRangeDialog(SaveRange(true,30,90),true,{saved=it},{},listOf(unknown)) } }
        screen.onNodeWithTag("save-range-slider").assertDoesNotExist()
        screen.onNodeWithText("開始時刻").performScrollTo().performTextReplacement("40")
        screen.onNodeWithText("適用").performClick()
        screen.runOnIdle { assertEquals(SaveRange(true,40,90),saved) }
    }
    @Test fun acceptsTimeRangeAndRejectsReversedTimes() {
        var saved: SaveRange?=null
        screen.setContent { MaterialTheme { SaveRangeDialog(SaveRange(),true,{saved=it},{}) } }
        screen.onNodeWithContentDescription("保存範囲を指定").performClick()
        screen.onNodeWithText("開始時刻").performScrollTo().performTextReplacement("1:30")
        screen.onNodeWithText("終了時刻").performScrollTo().performTextReplacement("1:00")
        screen.onNodeWithText("適用").performClick()
        screen.onNodeWithText("終了時刻は開始時刻より後にしてください。").performScrollTo().assertIsDisplayed()
        screen.runOnIdle { assertNull(saved) }
        screen.onNodeWithText("終了時刻").performScrollTo().performTextReplacement("2:00")
        screen.onNodeWithText("適用").performClick()
        screen.runOnIdle { assertEquals(SaveRange(true,90,120),saved) }
    }
    @Test fun blankEndMeansToEndAndTurningOffPreservesExistingTimes() {
        var saved: SaveRange?=null
        screen.setContent { MaterialTheme { SaveRangeDialog(SaveRange(true,30,90),true,{saved=it},{}) } }
        screen.onNodeWithText("終了時刻").performScrollTo().performTextReplacement("")
        screen.onNodeWithText("適用").performClick()
        screen.runOnIdle { assertEquals(SaveRange(true,30),saved) }
        screen.onNodeWithContentDescription("保存範囲を指定").performScrollTo().performClick()
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
