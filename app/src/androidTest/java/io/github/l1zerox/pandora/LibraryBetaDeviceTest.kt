package io.github.l1zerox.pandora

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class LibraryBetaDeviceTest {
    @get:Rule val screen=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun favoritesPlaylistsAndBookmarksSurviveReloadAndRemovalCleansReferences() {
        assumeTrue(BuildConfig.BETA)
        val prefs=context.getSharedPreferences("library_collections",0)
        val original=prefs.getString("data","{}")
        val uri="content://beta-test/${System.nanoTime()}"
        try {
            LibraryExtras.init(context)
            val id=LibraryExtras.create("通勤用",listOf(uri))
            LibraryExtras.favorite(uri)
            LibraryExtras.add(id,listOf(uri))
            LibraryExtras.rememberPosition(uri,27000,180000)
            LibraryExtras.init(context)
            assertTrue(uri in LibraryExtras.state.value.favorites)
            assertEquals(listOf(uri),LibraryExtras.state.value.playlists.single { it.id == id }.uris)
            assertEquals(27000L,LibraryExtras.position(uri,180000))
            LibraryExtras.rename(id,"お気に入りの音楽")
            LibraryExtras.init(context)
            assertEquals("お気に入りの音楽",LibraryExtras.state.value.playlists.single { it.id == id }.name)
            LibraryExtras.forget(uri)
            LibraryExtras.init(context)
            assertFalse(uri in LibraryExtras.state.value.favorites)
            assertTrue(LibraryExtras.state.value.playlists.single { it.id == id }.uris.isEmpty())
            assertEquals(0L,LibraryExtras.position(uri,180000))
        } finally {
            prefs.edit().putString("data",original).commit()
            context.getSharedPreferences("playback_bookmarks",0).edit().remove(uri).commit()
            LibraryExtras.init(context)
        }
    }

    @Test fun longPressSelectsWithoutPlayingAndSelectionReplacesThePlayButton() {
        assumeTrue(BuildConfig.BETA)
        val item=LibraryEntry("content://beta-test/missing","選択する動画","video/mp4",120000)
        val playback=LibraryPlayback(context)
        var selecting by mutableStateOf(false)
        var selected by mutableStateOf(false)
        var played=0
        screen.setContent { MaterialTheme {
            Box(Modifier.width(280.dp)) {
                LibraryCard(item,true,LibraryBackdrop(null,Rect.Zero,0f,Color.Black),playback,onDelete={},
                    selected=selected,selectionMode=selecting,onLongClick={selecting=true; selected=true}) {
                    if(selecting) selected=!selected else played++
                }
            }
        } }
        screen.onNodeWithText(item.title).performTouchInput { longClick() }
        screen.runOnIdle { assertTrue(selecting); assertTrue(selected); assertEquals(0,played) }
        screen.onNode(isToggleable()).assertIsOn().performClick().assertIsOff()
        screen.runOnIdle { assertEquals(0,played) }
    }
}
