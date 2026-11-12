package io.github.l1zerox.pandora

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryBrowseButtonsTest {
    @get:Rule val screen=createComposeRule()
    @Test fun folderAndSearchShareOneRowEvenWithTheCloseSearchLabel() {
        var expanded by mutableStateOf(false); var opened=0
        screen.setContent { MaterialTheme { Box(Modifier.width(280.dp)) {
            LibraryBrowseButtons(expanded,{opened++},{expanded=!expanded})
        } } }
        repeat(2) {
            val folder=screen.onNodeWithTag("library-folder-button").assertIsDisplayed()
            val search=screen.onNodeWithTag("library-search-button").assertIsDisplayed()
            val a=folder.fetchSemanticsNode().boundsInRoot; val b=search.fetchSemanticsNode().boundsInRoot
            assertEquals(a.center.y,b.center.y,1f); assertTrue(a.right < b.left)
            folder.performClick(); search.performClick()
        }
        screen.runOnIdle { assertEquals(2,opened); assertFalse(expanded) }
    }
}
