package io.github.l1zerox.pandora

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SaveRangeSliderTest {
    @get:Rule val screen=createComposeRule()
    @Test fun bothMarkersMatchTheTrackAtEndpointsAndInBothDirections() {
        var value by mutableStateOf(SaveRangeBarSelection(0,60))
        var direction by mutableStateOf(LayoutDirection.Ltr)
        screen.setContent { CompositionLocalProvider(LocalLayoutDirection provides direction) { MaterialTheme {
            Column(Modifier.width(320.dp)) { SaveRangeSlider(value,60,true,{value=it}) }
        } } }
        for(layout in listOf(LayoutDirection.Ltr,LayoutDirection.Rtl)) for(selection in listOf(SaveRangeBarSelection(0,60),SaveRangeBarSelection(10,40),SaveRangeBarSelection(29,30))) {
            screen.runOnIdle { direction=layout; value=selection }
            val track=screen.onNodeWithTag("save-range-track",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            for((tag,seconds) in listOf("save-range-start" to selection.start,"save-range-end" to selection.end)) {
                val marker=screen.onNodeWithTag(tag,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                val fraction=seconds/60f
                assertEquals(track.left+track.width*(if(layout == LayoutDirection.Ltr) fraction else 1-fraction),marker.center.x,1.2f)
                assertEquals(track.center.y,marker.center.y,1.2f)
                assertTrue(marker.height > marker.width*3)
            }
        }
    }
    @Test fun eachHandleDragsAndAccessibleProgressUsesTheSameSeconds() {
        var value by mutableStateOf(SaveRangeBarSelection(10,50))
        var enabled by mutableStateOf(true)
        screen.setContent { MaterialTheme { Column(Modifier.width(320.dp)) {
            SaveRangeSlider(value,60,enabled,{value=it})
        } } }
        val slider=screen.onNodeWithTag("save-range-slider")
        fun point(seconds: Float): Offset {
            val bounds=slider.fetchSemanticsNode().boundsInRoot
            val track=screen.onNodeWithTag("save-range-track",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            return Offset(track.left-bounds.left+track.width*seconds/60,track.center.y-bounds.top)
        }
        val first=point(10f); val startTarget=point(20f)
        slider.performTouchInput { down(first); moveTo(startTarget,300); up() }
        screen.runOnIdle { assertEquals(SaveRangeBarSelection(20,50),value) }
        val last=point(50f); val endTarget=point(40f)
        slider.performTouchInput { down(last); moveTo(endTarget,300); up() }
        screen.runOnIdle { assertEquals(SaveRangeBarSelection(20,40),value) }
        val handles=screen.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress),useUnmergedTree=true)
        handles[0].performSemanticsAction(SemanticsActions.SetProgress) { it(15.49f) }
        handles[1].performSemanticsAction(SemanticsActions.SetProgress) { it(30.6f) }
        screen.runOnIdle { assertEquals(SaveRangeBarSelection(15,31),value); enabled=false }
        handles[0].assertIsNotEnabled(); handles[1].assertIsNotEnabled()
        val locked=point(15f); val target=point(25f)
        slider.performTouchInput { down(locked); moveTo(target,300); up() }
        screen.runOnIdle { assertEquals(SaveRangeBarSelection(15,31),value) }
    }
}
