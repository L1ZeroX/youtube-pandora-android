package io.github.l1zerox.pandora

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Run on Android, not just desktop JVM: beta.9 crashed in Android's regex compiler. */
class LibraryAlbumRuntimeTest {
    @get:Rule val screen=createAndroidComposeRule<LibraryActivity>()

    @Test fun albumNormalizationAndLibraryOpeningWorkOnAndroid() {
        for(space in listOf(" ","\t","\n","\u0085","\u00a0","\u2003","\u202f","\u3000")) {
            assertEquals("μ's best album",albumNameKey("${space}μ’s${space}ＢＥＳＴ${space}Album$space"))
            assertEquals("",albumNameKey(space+"\u200b"+space))
        }
        val one=LibraryEntry("test:one","Song","audio/mpeg",album="μ's Best Album",artist="Group")
        val two=one.copy(uri="test:two",album=" μ’s\u3000Best Album ",artist="Solo")
        assertEquals(2,libraryAlbums(listOf(one,two),LibraryCollections()).single().tracks.size)
        // Read the device's existing entries without changing files or collection settings.
        val entries=runBlocking { LibraryRepository.load(screen.activity) }
        libraryAlbums(entries,LibraryCollections())
        screen.waitForIdle()
        screen.onNodeWithText("ライブラリー").assertIsDisplayed()
        screen.onNodeWithText("音楽",useUnmergedTree=true).performClick()
        screen.waitForIdle()
        screen.onNodeWithText("アルバム",useUnmergedTree=true).performClick()
        screen.waitForIdle()
        screen.onNodeWithText("ライブラリー").assertIsDisplayed()
    }
}
