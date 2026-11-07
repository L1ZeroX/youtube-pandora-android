package io.github.l1zerox.pandora

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ArtworkCropDialogTest {
    @get:Rule val screen=createComposeRule()
    @Test fun zoomResetConfirmAndCancelHaveSeparateEffects() {
        val bitmap=Bitmap.createBitmap(200,100,Bitmap.Config.ARGB_8888)
        var saved: ArtworkCrop? = null
        var dismissed=false
        var busy by mutableStateOf(false)
        screen.setContent { MaterialTheme {
            ArtworkCropDialog(bitmap,false,false,busy,"",{dismissed=true},{saved=it})
        } }
        screen.onNodeWithTag("artwork-crop-zoom").performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        screen.onNodeWithText("この範囲を使う").performClick()
        screen.runOnIdle { assertEquals(3f,saved!!.zoom,0f); saved=null }
        screen.onNodeWithText("位置・拡大をリセット").performClick()
        screen.onNodeWithText("この範囲を使う").performClick()
        screen.runOnIdle { assertEquals(ArtworkCrop(),saved); saved=null; busy=true }
        screen.onNodeWithText("この範囲を使う").assertIsNotEnabled()
        screen.onNodeWithText("キャンセル").performClick()
        screen.runOnIdle { assertTrue(dismissed); assertNull(saved) }
    }
}
