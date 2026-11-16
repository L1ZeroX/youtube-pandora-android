package io.github.l1zerox.pandora

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentLinkedQueue

class SaveRangeFramePreviewTest {
    @get:Rule val screen=createComposeRule()
    private val video=Video("one","https://example.com/one","One",duration=18.0)
    private fun board(url: String)=SaveRangeStoryboard(20,12,2,2,.5,18.0,listOf(
        StoryboardSheet("$url/0",0.0,8.0,4),StoryboardSheet("$url/1",8.0,8.0,4),StoryboardSheet("$url/2",16.0,2.0,1)))
    private fun solid(color: Int)=Bitmap.createBitmap(20,12,Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
    @Test fun rangeHandlesAndTypedTimesRequestTheCorrectImagesWithoutChangingTheSavedRange() {
        val requests=ConcurrentLinkedQueue<StoryboardTile>()
        val loader=object: RangePreviewLoader {
            override suspend fun storyboard(video: Video)=board(video.url)
            override suspend fun frame(tile: StoryboardTile): Bitmap { requests.add(tile); return solid(Color.RED) }
        }
        var saved: SaveRange?=null
        screen.setContent { MaterialTheme { SaveRangeDialog(SaveRange(true,0,18),true,{saved=it},{},listOf(video),previewLoader=loader) } }
        screen.waitUntil(5000) { requests.contains(board(video.url).tile(0)) && requests.contains(board(video.url).tile(18,true)) }
        val handles=screen.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress),useUnmergedTree=true)
        screen.onNodeWithTag("save-range-slider").performScrollTo()
        handles[0].performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        handles[1].performSemanticsAction(SemanticsActions.SetProgress) { it(8f) }
        screen.waitUntil(5000) { requests.contains(board(video.url).tile(4)) && requests.contains(board(video.url).tile(8,true)) }
        screen.onNodeWithText("開始時刻").performScrollTo().performTextReplacement("0:10")
        screen.waitUntil(5000) { requests.contains(board(video.url).tile(10)) }
        screen.onNodeWithText("終了時刻").performScrollTo().performTextReplacement("0:16")
        screen.onNodeWithText("適用").performClick()
        screen.runOnIdle { assertEquals(SaveRange(true,10,16),saved) }
    }
    @Test fun disabledRangeDoesNotFetchAndUnavailablePreviewKeepsTheBarAndManualInputWorking() {
        var lookups=0; var saved: SaveRange?=null
        val loader=object: RangePreviewLoader {
            override suspend fun storyboard(video: Video): SaveRangeStoryboard? { lookups++; return null }
            override suspend fun frame(tile: StoryboardTile): Bitmap=error("No storyboard")
        }
        screen.setContent { MaterialTheme { SaveRangeDialog(SaveRange(),true,{saved=it},{},listOf(video),previewLoader=loader) } }
        screen.runOnIdle { assertEquals(0,lookups) }
        screen.onNodeWithContentDescription("保存範囲を指定").performClick()
        screen.onNodeWithText("この動画の位置プレビューを取得できません。範囲指定は使えます。").performScrollTo().assertIsDisplayed()
        screen.onNodeWithTag("save-range-slider").assertExists()
        screen.onNodeWithText("開始時刻").performScrollTo().performTextReplacement("0:02")
        screen.onNodeWithText("適用").performClick()
        screen.runOnIdle { assertEquals(SaveRange(true,2,18),saved); assertEquals(1,lookups) }
    }
    @Test fun changingReferenceCannotShowThePreviousVideosLateResult() {
        val pending=CompletableDeferred<Unit>()
        var selected by mutableStateOf(video)
        val loader=object: RangePreviewLoader {
            override suspend fun storyboard(video: Video): SaveRangeStoryboard {
                if(video.key == "one") withContext(NonCancellable) { pending.await() }
                return board(video.url)
            }
            override suspend fun frame(tile: StoryboardTile)=solid(if(tile.url.contains("/one/")) Color.RED else Color.BLUE)
        }
        screen.setContent { MaterialTheme { SaveRangeFramePreviews(selected,SaveRangeBarSelection(0,18),loader) } }
        screen.runOnIdle { selected=video.copy(key="two",url="https://example.com/two") }
        screen.waitUntil(5000) { screen.onAllNodesWithContentDescription("開始位置のプレビュー").fetchSemanticsNodes().isNotEmpty() }
        screen.runOnIdle { pending.complete(Unit) }
        screen.waitForIdle()
        val pixels=screen.onNodeWithTag("save-range-preview-開始").captureToImage().toPixelMap()
        assertTrue(pixels[pixels.width/2,pixels.height/2].blue > .9f)
        assertTrue(pixels[pixels.width/2,pixels.height/2].red < .1f)
    }
    @Test fun continuingToDragFinishesTheSheetRequestThenLoadsTheLatestCell() {
        val pending=CompletableDeferred<Unit>()
        val requests=ConcurrentLinkedQueue<StoryboardTile>()
        var selection by mutableStateOf(SaveRangeBarSelection(0,18))
        var cancelled=0
        val loader=object: RangePreviewLoader {
            override suspend fun storyboard(video: Video)=board(video.url)
            override suspend fun frame(tile: StoryboardTile): Bitmap {
                requests.add(tile)
                if(tile == board(video.url).tile(0)) try { pending.await() }
                catch(e: CancellationException) { cancelled++; throw e }
                return solid(Color.BLUE)
            }
        }
        screen.setContent { MaterialTheme { SaveRangeFramePreviews(video,selection,loader) } }
        screen.waitUntil(5000) { requests.contains(board(video.url).tile(0)) }
        screen.runOnIdle { selection=SaveRangeBarSelection(2,18) }
        screen.runOnIdle { selection=SaveRangeBarSelection(4,18) }
        screen.runOnIdle { assertEquals(0,cancelled); pending.complete(Unit) }
        screen.waitUntil(5000) { requests.contains(board(video.url).tile(4)) }
        screen.runOnIdle { assertEquals(0,cancelled) }
    }
    @Test fun regionDecodeReturnsOnlyTheChosenCellAndRejectsOutOfBounds() {
        val sheet=Bitmap.createBitmap(40,24,Bitmap.Config.ARGB_8888)
        for(y in 0 until 24) for(x in 0 until 40) sheet.setPixel(x,y,when {
            y < 12 && x < 20 -> Color.RED
            y < 12 -> Color.GREEN
            x < 20 -> Color.BLUE
            else -> Color.YELLOW
        })
        val bytes=try { ByteArrayOutputStream().use { sheet.compress(Bitmap.CompressFormat.PNG,100,it); it.toByteArray() } }
            finally { sheet.recycle() }
        val decoded=decodeStoryboardTile(bytes,StoryboardTile("unused",20,12,20,12))
        try { assertEquals(20,decoded.width); assertEquals(12,decoded.height); assertEquals(Color.YELLOW,decoded.getPixel(10,6)) }
        finally { decoded.recycle() }
        try { decodeStoryboardTile(bytes,StoryboardTile("unused",40,12,20,12)); fail("Out-of-bounds image was accepted") }
        catch(_: IllegalArgumentException) { }
    }
}
