package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class BatchDeletionStateTest {
    @Test fun deniedOrFailedItemsAreNeverReportedAsDeletedAndLaterItemsCanComplete() {
        val items=(1..3).map { LibraryEntry("content://media/$it","Item $it","video/mp4") }
        var state=BatchDeletionState("test",items)
        state=state.completed(true)
        assertEquals(setOf(items[0].uri),state.deleted)
        state=state.completed(false)
        assertFalse(state.finished); assertEquals(1,state.failed)
        assertFalse(items[1].uri in state.deleted)
        state=state.completed(true)
        assertTrue(state.finished); assertEquals(3,state.index)
        assertEquals(setOf(items[0].uri,items[2].uri),state.deleted)
        assertThrows(IllegalStateException::class.java) { state.completed(true) }
    }
}
