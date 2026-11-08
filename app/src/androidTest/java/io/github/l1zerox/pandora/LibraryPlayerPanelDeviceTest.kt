package io.github.l1zerox.pandora

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryPlayerPanelDeviceTest {
    @get:Rule val screen=createComposeRule()

    @Test fun visibilityAndSizeSurviveReloadWhileOldPreferencesUseCompactDefaults() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val original=Store.state.value
        val prefs=context.getSharedPreferences("pandora",0)
        try {
            val changed=original.preferences.copy(libraryPlayerVisible=false,libraryPlayerScale=.9f,libraryPlayerWave=false)
            Store.preferences(changed); Store.init(context)
            assertEquals(changed,Store.state.value.preferences)
            val old=JSONObject(prefs.getString("preferences","{}")!!).apply {
                remove("libraryPlayerVisible"); remove("libraryPlayerScale"); remove("libraryPlayerWave")
            }
            prefs.edit().putString("preferences",old.toString()).commit(); Store.init(context)
            assertTrue(Store.state.value.preferences.libraryPlayerVisible)
            assertEquals(.7f,Store.state.value.preferences.libraryPlayerScale,0f)
            assertTrue(Store.state.value.preferences.libraryPlayerWave)
        } finally { Store.state.value=original; Store.save() }
    }

    @Test fun panelControlsSeekAndResizeWithoutReplacingThePlaybackState() {
        val artwork=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
        var scale by mutableFloatStateOf(.7f)
        var state by mutableStateOf(LibraryPlayerState("music","長い曲名のプレビュー",playing=true,position=16_000,duration=263_000,
            seekable=true,canPlay=true,canPrevious=true,canNext=true))
        var opened=0; var previous=0; var next=0
        screen.setContent { MaterialTheme {
            LibraryPlayerPanel(state,artwork,Color.Red,scale,onOpen={opened++},onToggle={state=state.copy(playing=!state.playing)},
                onPrevious={previous++},onNext={next++},onSeek={state=state.copy(position=playbackPositionMs(state.duration,it))},
                modifier=Modifier.width(320.dp))
        } }
        val small=screen.onNodeWithTag("library-player-panel").fetchSemanticsNode().boundsInRoot.height
        screen.onNodeWithContentDescription("再生パネルを一時停止").performClick()
        screen.onNodeWithContentDescription("再生パネルで再生").assertIsDisplayed()
        screen.onNodeWithContentDescription("再生パネルの前へ").performClick()
        screen.onNodeWithContentDescription("再生パネルの次へ").performClick()
        screen.onNodeWithContentDescription("再生画面を開く").performClick()
        screen.onNodeWithContentDescription("再生パネルの再生位置")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(.5f) }
        screen.runOnIdle {
            assertFalse(state.playing); assertEquals(131_500L,state.position)
            assertEquals(1,previous); assertEquals(1,next); assertEquals(1,opened)
            scale=1f
        }
        assertTrue(screen.onNodeWithTag("library-player-panel").fetchSemanticsNode().boundsInRoot.height > small)
        screen.runOnIdle { state=state.copy(canNext=false,seekable=false) }
        screen.onNodeWithContentDescription("再生パネルの次へ").assertIsNotEnabled()
        screen.onNodeWithContentDescription("再生パネルの再生位置").assertIsNotEnabled()
    }

    @Test fun controlsHideAndRestoreOnlyThePanelAndPreserveSize() {
        var preferences by mutableStateOf(Preferences(libraryPlayerScale=.85f))
        val playback=LibraryPlayerState("music","再生を維持",playing=true,position=16_000,duration=263_000)
        var playbackActions=0
        screen.setContent { MaterialTheme {
            Column(Modifier.width(300.dp)) {
                LibraryPlayerAppearanceControls(preferences) { preferences=it }
                if(preferences.libraryPlayerVisible) LibraryPlayerPanel(playback,null,null,preferences.libraryPlayerScale,
                    onOpen={playbackActions++},onToggle={playbackActions++},onPrevious={playbackActions++},onNext={playbackActions++},onSeek={playbackActions++})
            }
        } }
        screen.onNodeWithContentDescription("再生パネルを表示").performClick()
        screen.onNodeWithTag("library-player-panel").assertDoesNotExist()
        screen.onNodeWithContentDescription("再生パネルの大きさ").assertIsNotEnabled()
        screen.onNodeWithContentDescription("再生パネルを表示").performClick()
        screen.onNodeWithTag("library-player-panel").assertIsDisplayed()
        screen.runOnIdle { assertEquals(.85f,preferences.libraryPlayerScale,0f); assertEquals(0,playbackActions) }
        screen.onNodeWithContentDescription("再生パネルの大きさ")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        screen.runOnIdle { assertEquals(1f,preferences.libraryPlayerScale,0f); assertEquals(0,playbackActions) }
    }
}
