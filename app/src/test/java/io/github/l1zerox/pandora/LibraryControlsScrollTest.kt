package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class LibraryControlsScrollTest {
    @Test fun reachingBottomCollapsesButMiddleOrProgrammaticScrollingDoesNot() {
        val gesture=LibraryControlsScroll(64f)
        assertNull(gesture.scroll(true,false,false,-80f,0f,true))
        assertEquals(false,gesture.scroll(true,false,true,-5f,0f,true))
        assertNull(gesture.scroll(true,false,true,-200f,0f,false))
        assertNull(gesture.endFling(true,true))
    }
    @Test fun userFlingToBottomCollapsesAndReversingDirectionCancelsIt() {
        val gesture=LibraryControlsScroll(64f)
        gesture.scroll(true,false,false,-90f,0f,true)
        assertEquals(false,gesture.endFling(true,true))
        gesture.scroll(true,false,false,-90f,0f,true)
        gesture.scroll(true,false,false,10f,0f,true)
        assertNull(gesture.endFling(true,true))
    }
    @Test fun topPullStillExpandsAndShortListsCanCollapseWithoutChangingFilters() {
        val gesture=LibraryControlsScroll(64f)
        assertNull(gesture.scroll(false,true,true,0f,40f,true))
        assertEquals(true,gesture.scroll(false,true,true,0f,25f,true))
        assertEquals(false,gesture.scroll(true,true,true,0f,-65f,true))
        assertNull(gesture.scroll(false,true,true,0f,100f,false))
        assertNull(gesture.endFling(false,true))
    }
}
