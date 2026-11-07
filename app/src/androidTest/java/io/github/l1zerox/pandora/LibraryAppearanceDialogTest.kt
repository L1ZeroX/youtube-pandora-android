package io.github.l1zerox.pandora

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryAppearanceDialogTest {
    @get:Rule val screen=createComposeRule()

    @Test fun headerKeepsTitleCenteredWithEqualButtonsAndLoadingDoesNotDisableBack() {
        var loading by mutableStateOf(true)
        var back=0; var refresh=0
        screen.setContent { MaterialTheme {
            Column(Modifier.width(280.dp)) {
                LibraryHeader(Color(0xFF225599),loading,{back++},{refresh++})
            }
        } }
        fun checkCenter() {
            val header=screen.onNodeWithTag("library-header").fetchSemanticsNode().boundsInRoot
            val heading=screen.onNodeWithTag("library-heading").fetchSemanticsNode().boundsInRoot
            val left=screen.onNodeWithTag("library-back").fetchSemanticsNode().boundsInRoot
            val right=screen.onNodeWithTag("library-refresh").fetchSemanticsNode().boundsInRoot
            assertEquals(header.center.x,heading.center.x,1f)
            assertEquals(left.width,right.width,1f)
        }
        checkCenter()
        screen.onNodeWithTag("library-refresh").assertIsNotEnabled()
        screen.onNodeWithTag("library-back").performClick()
        screen.runOnIdle { assertEquals(1,back); loading=false }
        checkCenter()
        screen.onNodeWithTag("library-refresh").performClick()
        screen.runOnIdle { assertEquals(1,refresh) }
    }

    @Test fun previewReflectsControlsAndResetWithoutChangingSharedWallpaperSettings() {
        val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply {
            for(x in 0 until width) for(y in 0 until height) setPixel(x,y,if((x/8+y/8)%2 == 0) android.graphics.Color.BLUE else android.graphics.Color.WHITE)
        }
        val original=Preferences(opacity=.25f,glass=.6f,blur=8f)
        var preferences by mutableStateOf(original)
        var dismissed=false
        screen.setContent { MaterialTheme {
            LibraryAppearanceDialog(bitmap,preferences,null,Color(0xFF225599),darkColorScheme(),{preferences=it},{dismissed=true})
        } }
        screen.onNodeWithContentDescription("ライブラリーパネルの透過").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(.7f) }
        screen.onNodeWithContentDescription("ライブラリーパネルのぼかし").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(15f) }
        screen.onNodeWithTag("library-appearance-preview")
            .assertContentDescriptionEquals("ライブラリーの表示サンプル。パネル透過率 70%、ぼかし 15")
        screen.runOnIdle {
            assertEquals(original.copy(libraryGlass=.7f,libraryBlur=15),preferences)
        }
        screen.onNodeWithText("標準に戻す").performScrollTo().performClick()
        screen.onNodeWithTag("library-appearance-preview")
            .assertContentDescriptionEquals("ライブラリーの表示サンプル。パネル透過率 45%、ぼかし 10")
        screen.onNodeWithText("完了").performClick()
        screen.runOnIdle { assertTrue(dismissed) }
    }
}
