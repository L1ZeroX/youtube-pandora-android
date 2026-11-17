package io.github.l1zerox.pandora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryIndexRailTest {
    @get:Rule val screen=createComposeRule()
    private fun railBackground(): Float {
        val image=screen.onNodeWithTag("library-index-rail").captureToImage().toPixelMap()
        return image[image.width/2,image.height/8].red
    }
    @Test fun normalGridDragRevealsTranslucentLettersWithoutJumpingOrChangingSortThenFadesAway() {
        var starts=0; var jumps=0
        screen.setContent { MaterialTheme {
            val grid=rememberLazyGridState()
            Box(Modifier.size(240.dp,320.dp).background(Color.White)) {
                LazyVerticalGrid(GridCells.Fixed(1),state=grid,
                    modifier=Modifier.fillMaxSize().padding(end=24.dp).testTag("test-library-grid")) {
                    items(100) { Text("Row $it",Modifier.height(48.dp)) }
                }
                LibraryIndexRail(mapOf("A" to 0,"B" to 50),{starts++},{jumps++},grid.isScrollInProgress)
            }
        } }
        screen.mainClock.autoAdvance=false
        screen.onNodeWithText("A").assertDoesNotExist()
        val grid=screen.onNodeWithTag("test-library-grid")
        grid.performTouchInput { down(Offset(width*.4f,height*.8f)); moveTo(Offset(width*.4f,height*.2f),300) }
        screen.mainClock.advanceTimeBy(240)
        screen.onNodeWithText("A").assertIsDisplayed()
        screen.onNodeWithText("B").assertIsDisplayed()
        assertTrue("Index should be translucent over a white background",railBackground() in .45f.. .85f)
        screen.onNodeWithTag("library-index-preview").assertDoesNotExist()
        screen.runOnIdle { assertEquals(0,starts); assertEquals(0,jumps) }
        grid.performTouchInput { up() }
        screen.mainClock.advanceTimeBy(5000)
        screen.onNodeWithText("A").assertDoesNotExist()
        screen.runOnIdle { assertEquals(0,starts); assertEquals(0,jumps) }
    }
    @Test fun edgeDragStillSelectsLettersAndShowsTheOpaqueActiveIndex() {
        var starts=0; val jumps=mutableListOf<String?>()
        screen.setContent { MaterialTheme { Box(Modifier.size(240.dp,320.dp).background(Color.White)) {
            LibraryIndexRail(mapOf("A" to 0,"B" to 50),{starts++},{jumps.add(it)})
        } } }
        screen.mainClock.autoAdvance=false
        val rail=screen.onNodeWithTag("library-index-rail")
        rail.performTouchInput { down(Offset(width/2f,height*.2f)); moveTo(Offset(width/2f,height*.8f),250) }
        screen.mainClock.advanceTimeBy(240)
        screen.onNodeWithTag("library-index-preview").assertIsDisplayed()
        assertTrue("Active index should be stronger than passive scrolling",railBackground() < .4f)
        screen.runOnIdle { assertEquals(1,starts); assertEquals("B",jumps.last()) }
        rail.performTouchInput { up() }
        screen.mainClock.advanceTimeBy(1500)
        screen.onNodeWithTag("library-index-preview").assertDoesNotExist()
        screen.onNodeWithText("A").assertDoesNotExist()
        screen.runOnIdle { assertNull(jumps.last()) }
    }
}
