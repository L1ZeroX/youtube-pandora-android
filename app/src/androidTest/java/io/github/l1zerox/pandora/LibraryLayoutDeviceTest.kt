package io.github.l1zerox.pandora

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryLayoutDeviceTest {
    @get:Rule val screen=createComposeRule()

    @Test fun layoutsPersistAndOldOrUnknownPreferencesFallBackToStandard() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val original=Store.state.value
        val prefs=context.getSharedPreferences("pandora",0)
        try {
            for(layout in LibraryLayout.entries) {
                Store.preferences(original.preferences.copy(libraryLayout=layout.id))
                Store.init(context)
                assertEquals(layout.id,Store.state.value.preferences.libraryLayout)
                assertEquals(original.preferences.copy(libraryLayout=layout.id),Store.state.value.preferences)
            }
            for(id in listOf(null,"unknown-layout")) {
                val json=JSONObject(prefs.getString("preferences","{}")!!)
                if(id == null) json.remove("libraryLayout") else json.put("libraryLayout",id)
                prefs.edit().putString("preferences",json.toString()).commit()
                Store.init(context)
                assertEquals("standard",Store.state.value.preferences.libraryLayout)
            }
        } finally { Store.state.value=original; Store.save() }
    }

    @Test fun switcherKeepsPlayAndDeleteSeparateInEveryLayoutIncludingNarrowGrid() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val entry=LibraryEntry("content://layout-test/missing","レイアウトのテスト動画","video/mp4",123_000,autoDubbed=true)
        val playback=LibraryPlayback(context)
        val backdrop=LibraryBackdrop(null,Rect.Zero,0f,Color.Black)
        var layout by mutableStateOf(LibraryLayout.STANDARD)
        var played=0; var deleted=0
        screen.setContent { MaterialTheme {
            Column(Modifier.width(280.dp)) {
                LibraryLayoutButton(layout,Color(0xFF225599),onOpen={},onSelect={layout=it})
                Box(Modifier.width(when(layout.columns) { 3 -> 88.dp; 2 -> 135.dp; else -> 280.dp }).testTag("card")) {
                    LibraryCard(entry,true,backdrop,playback,layout,onDelete={deleted++},onClick={played++})
                }
            }
        } }
        for((index,option) in LibraryLayout.entries.withIndex()) {
            screen.onNodeWithContentDescription("レイアウトを変更").performClick()
            screen.onNodeWithText(option.label).performClick()
            screen.onNodeWithContentDescription("レイアウトを変更")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,option.label))
            screen.onNodeWithText("オートダビング版").assertIsDisplayed()
            val play=screen.onNodeWithContentDescription("再生").assertIsDisplayed()
            val menu=screen.onNodeWithContentDescription("${entry.title}のメニュー").assertIsDisplayed()
            val card=screen.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
            val playBounds=play.fetchSemanticsNode().boundsInRoot
            val menuBounds=menu.fetchSemanticsNode().boundsInRoot
            assertTrue(card.contains(playBounds.center)); assertTrue(card.contains(menuBounds.center))
            assertFalse(playBounds.overlaps(menuBounds))
            play.performTouchInput { click() }
            menu.performTouchInput { click() }
            screen.onNodeWithText("削除").performClick()
            screen.runOnIdle { assertEquals(index+1,played); assertEquals(index+1,deleted) }
        }
    }

    @Test fun appearancePreviewUsesSelectedLayoutAndPreservesGlassSettings() {
        val original=Preferences(libraryGlass=.7f,libraryBlur=15)
        var preferences by mutableStateOf(original)
        screen.setContent { MaterialTheme {
            LibraryAppearanceDialog(null,preferences,null,Color(0xFF225599),darkColorScheme(),{preferences=it},{})
        } }
        for(layout in LibraryLayout.entries) {
            screen.onNodeWithText(layout.label).performScrollTo().performClick()
            screen.onNodeWithTag("library-appearance-preview")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,layout.label))
                .assertContentDescriptionEquals("ライブラリーの表示サンプル。パネル透過率 70%、ぼかし 15")
            screen.runOnIdle { assertEquals(original.copy(libraryLayout=layout.id),preferences) }
        }
    }
}
