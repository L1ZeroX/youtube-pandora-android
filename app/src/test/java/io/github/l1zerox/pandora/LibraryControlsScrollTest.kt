package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class LibraryControlsScrollTest {
    @Test fun firstDownwardMovementCollapsesButProgrammaticScrollingDoesNot() {
        val gesture=LibraryControlsScroll(64f)
        assertEquals(false,gesture.scroll(true,false,-.25f,0f,true))
        assertEquals(false,gesture.scroll(true,true,-1f,0f,true))
        assertNull(gesture.scroll(true,false,-200f,0f,false))
        assertNull(gesture.scroll(true,false,0f,0f,true))
    }
    @Test fun oppositeDirectionKeepsToolsOpenAndSeparatePullsDoNotAccumulate() {
        val gesture=LibraryControlsScroll(64f)
        assertNull(gesture.scroll(true,false,30f,0f,true))
        assertNull(gesture.scroll(false,true,0f,40f,true))
        gesture.endGesture()
        assertNull(gesture.scroll(false,true,0f,40f,true))
        assertEquals(true,gesture.scroll(false,true,0f,25f,true))
    }
    @Test fun topPullStillExpandsAndShortListsCanCollapseWithoutChangingFilters() {
        val gesture=LibraryControlsScroll(64f)
        assertNull(gesture.scroll(false,true,0f,40f,true))
        assertEquals(true,gesture.scroll(false,true,0f,25f,true))
        assertEquals(false,gesture.scroll(true,true,0f,-1f,true))
        assertNull(gesture.scroll(false,true,0f,100f,false))
    }
}
