package io.github.l1zerox.pandora

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlaybackPositionSliderTest {
    @get:Rule val screen=createComposeRule()

    @Test fun markerAndTrackShareCenterAndEndpointsAtBothSizesAndDirections() {
        var compact by mutableStateOf(true)
        var width by mutableStateOf(88.dp)
        var value by mutableFloatStateOf(0f)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        var waveform by mutableStateOf(false)
        var effect by mutableStateOf(PlaybackEffect.WAVE)
        screen.setContent { CompositionLocalProvider(LocalLayoutDirection provides direction) { MaterialTheme {
            Column(Modifier.width(width)) {
                PlaybackPositionSlider("a",value,{value=it},{},true,Color.Red,"位置テスト",Modifier.fillMaxWidth(),compact,waveform=waveform,effect=effect)
            }
        } } }
        for(style in PlaybackEffect.entries) for(wave in listOf(false,true)) for(small in listOf(true,false)) for(size in listOf(88.dp,320.dp))
            for(layoutDirection in listOf(LayoutDirection.Ltr,LayoutDirection.Rtl))
                for(fraction in listOf(0f,16f/263f,.5f,1f)) {
                    screen.runOnIdle { effect=style; waveform=wave; compact=small; width=size; direction=layoutDirection; value=fraction }
                    val marker=screen.onNodeWithTag("playback-position-marker",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                    val track=screen.onNodeWithTag("playback-position-track",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                    val expected=track.left+track.width*(if(layoutDirection == LayoutDirection.Rtl) 1-fraction else fraction)
                    assertEquals(expected,marker.center.x,1.2f)
                    assertEquals(track.center.y,marker.center.y,1.2f)
                    assertTrue(marker.height > marker.width*3)
                }
    }

    @Test fun tappingDraggingAndAccessibilitySeekToTheDisplayedTrackPosition() {
        var value by mutableFloatStateOf(0f)
        var enabled by mutableStateOf(true)
        val committed=mutableListOf<Float>()
        screen.setContent { MaterialTheme {
            Column(Modifier.width(320.dp)) {
                PlaybackPositionSlider("a",value,{value=it},{committed+=value},enabled,Color.Red,"位置テスト",Modifier.fillMaxWidth(),true,waveform=true,effect=PlaybackEffect.NOTIFICATION)
            }
        } }
        val slider=screen.onNodeWithContentDescription("位置テスト")
        fun point(fraction: Float): Offset {
            val root=slider.fetchSemanticsNode().boundsInRoot
            val track=screen.onNodeWithTag("playback-position-track",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            return Offset(track.left-root.left+track.width*fraction,track.center.y-root.top)
        }
        val quarter=point(.25f)
        slider.performTouchInput { click(quarter) }
        screen.runOnIdle { assertEquals(.25f,value,.01f); assertEquals(value,committed.last(),.001f) }
        val start=point(.25f); val end=point(.75f)
        slider.performTouchInput { down(start); moveTo(end,200); up() }
        screen.runOnIdle { assertEquals(.75f,value,.015f); assertEquals(value,committed.last(),.001f) }
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(.5f) }
        screen.runOnIdle { assertEquals(.5f,value,.001f); assertEquals(value,committed.last(),.001f); enabled=false }
        val count=committed.size
        slider.assertIsNotEnabled().performTouchInput { click(quarter) }
        screen.runOnIdle { assertEquals(.5f,value,.001f); assertEquals(count,committed.size) }
    }

    @Test fun switchingMediaDuringDragDoesNotSeekTheNewItem() {
        var current by mutableStateOf("a")
        val commands=mutableListOf<String>()
        screen.setContent { MaterialTheme {
            val uri=current
            var value by remember(uri) { mutableFloatStateOf(.25f) }
            Column(Modifier.width(320.dp)) {
                PlaybackPositionSlider(uri,value,{value=it},{if(uri == current) commands+=uri},true,Color.Red,"位置テスト",Modifier.fillMaxWidth(),true,waveform=true)
            }
        } }
        val slider=screen.onNodeWithContentDescription("位置テスト")
        slider.performTouchInput { down(Offset(width*.25f,centerY)); moveTo(Offset(width*.7f,centerY),200) }
        screen.runOnIdle { current="b" }
        slider.performTouchInput { up() }
        screen.runOnIdle { assertFalse(commands.contains("b")) }
        slider.assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(.25f,0f..1f))
    }
}
